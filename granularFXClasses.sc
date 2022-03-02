
// granularFX GUI Classes

// Save and load preferences
// Reference: https://github.com/neilcosgrove/LNX_Studio/blob/master/SCClassLibrary/LNX_Studio%20Library/1.%20LNX_Studio/12.%20LNX_File.sc#L4
GranulatorPreferences {
	classvar <prefDir;

	*initClass {
		prefDir = String.scDir++"/preferences/".absolutePath;
		prefDir.mkdir();
		postf("Saving preferences in `%`\n", prefDir);
	}

	*savePref {
		arg path, list;
		this.save(prefDir+/+path, list);
	}

	*loadPref{
		arg path;
		^this.load(prefDir+/+path);
	}

	*save {
		arg path, list;
		var file;
		{
			file = File(path,"w");
			list.do({
				arg line;
				file.write(line.asString++"\n");
			});
			file.close;
		}.defer(0.01); // for safe mode start-up
	}

	*load {
		arg path;
		var file, list, line;
		if (File.exists(path).not) {^nil};
		file = File(path, "r");
		line = file.getLine;
		list = [];
		while ( {line.notNil}, {
			list = list.add(line);
			line = file.getLine;
		});
		file.close;
		^list
	}

	*delete { arg path; path.removeFile(silent:true); }

	*deletePref { arg path; this.delete(prefDir++path); }
}

GranulatorSetup {
	var server, bufferLength, params;
	var <buffer;
	// Buses
	var <micBus, <ptrBus, <masterBus;
	var <>buses, <>busIDs;
	// Groups for managing signal flow
	var <micGrp, <ptrGrp, <recGrp, <grainGrp, <masterGrp;
	var <>groups, <>groupIDs;
	// Synths
	var <micSynth, <ptrSynth, <recSynth, <masterSynth;
	var <>synths, <>synthIDs;
	// in/out Buses
	var <inBus, <outBus;

	var masterGainParam, masterMixParam, masterTempoParam;

	*new {
		arg server, bufferLength, params, inBus, outBus;
		^super.newCopyArgs(server, bufferLength, params, inBus, outBus);
	}

	init {
		buffer = Buffer.alloc(server, server.sampleRate * bufferLength,1);

		micBus = Bus.audio(server, 2);
		ptrBus = Bus.audio(server, 1);
		masterBus = Bus.audio(server, 2);

		buses = (
			\mic: micBus,
			\ptr: ptrBus,
			\master: masterBus
		);

		busIDs = buses.collect(_.index);

		micGrp = Group.new;
		ptrGrp = Group.after(micGrp);
		recGrp = Group.after(ptrGrp);
		grainGrp = Group.after(recGrp);
		masterGrp = Group.after(grainGrp);

		groups = (
			\mic: micGrp,
			\ptr: ptrGrp,
			\rec: recGrp,
			\grain: grainGrp,
			\master: masterGrp
		);

		groupIDs = groups.collect(_.nodeID);

		this.initUGens;

		// Wait 0.1s after SynthDefs are created
		{this.createSynths}.defer(0.1);
	}

	initUGens {
		SynthDef.new(\mic, {
		    arg in=0, out=0, amp=1;
		    var sig;
			// Assume 2 channel Input device
		    sig = [SoundIn.ar(bus:in),SoundIn.ar(bus:(in+1))] * amp;
		    Out.ar(bus:out,channelsArray:sig);
		}).send(server);

		SynthDef.new(\ptr, {
		    arg out=0, buf=0;
		    var sig;
		    sig = Phasor.ar(
		        trig:0,
		        rate:BufRateScale.kr(buf),
		        start:0,
		        end:BufFrames.kr(buf)
		    );
		    Out.ar(out, sig);
		}).send(server);

		SynthDef.new(\rec, {
		    arg ptrIn=0, micIn=0, buf=0;
		    var ptr, sig;
		    ptr = In.ar(bus:ptrIn, numChannels:1);
		    sig = In.ar(bus:micIn, numChannels:1);
		    BufWr.ar(
		        inputArray:sig,
		        bufnum:buf,
		        phase:ptr,
		        loop:1
		    );
		}).send(server);

		SynthDef.new(\master, {
			arg dryIn=0, wetIn=0, out=0, gain=0.8, mix=0.0;
			var dry, wet, sig;
			dry = In.ar(dryIn, numChannels: 2);
			wet = In.ar(wetIn, numChannels: 2);
			sig = ((dry * (1.0-mix) ) + (wet * mix)) * gain;
			Out.ar(out, sig);
		}).send(server);
	}

	createSynths {
		// Params
		masterGainParam = params.getParam("%%".format(\Gain, "Master").asSymbol);
		masterMixParam = params.getParam("%%".format(\Mix, "Master").asSymbol);
		masterTempoParam = params.getParam("%%".format(\Tempo, "Master").asSymbol);

		masterGainParam.addListener(\UpdateMasterSynth, { AppClock.sched(0,{ masterSynth.set(\gain, masterGainParam.get); }); });
		masterMixParam.addListener(\UpdateMasterSynth, { AppClock.sched(0,{ masterSynth.set(\mix, masterMixParam.get); }); });
		// masterTempoParam.addListener(\UpdateMasterSynth, { AppClock.sched(0,{  }); });

		micSynth = Synth(\mic, [\in, inBus, \out, micBus], micGrp);
		ptrSynth = Synth(\ptr, [\buf, buffer, \out, ptrBus], ptrGrp);
		recSynth = Synth(\rec, [\ptrIn, ptrBus, \micIn, micBus, \buf, buffer], recGrp);
		masterSynth = Synth(\master, [
			\dryIn, micBus,
			\wetIn, masterBus,
			\out, outBus,
			\gain, masterGainParam.get,
			\mix, masterMixParam.get
		], masterGrp);

		synths = (
			\mic: micSynth,
			\ptr: ptrSynth,
			\rec: recSynth,
			\master: masterSynth
		);

		synthIDs = synths.collect(_.nodeID);
	}

	freeBuses {
		buses.do(_.free);
		buses = nil;
		busIDs = nil;
	}

	freeGroups {
		groups.do(_.free);
		groups = nil;
		groupIDs = nil;
	}

	freeSynths {
		synths.do(_.free);
		synths = nil;
		synthIDs = nil;
	}

	freeAll {
		this.freeBuses;
		this.freeSynths;
		this.freeGroups;
		server.defaultGroup.deepFree;
		buffer.free;
	}
}

GranulatorSynth {
	var server, synthId, synthfxname, buffer, ptrBus, outBus, grainGrp, params;
	var synthfx;

	var gainParam, grainDensityParam, grainSizeParam,
	delayParam, pitchParam, stereoWidthParam,
	onStateParam, syncModeParam, masterTempoParam;

	*new {
		arg server, synthId, synthfxname, buffer, ptrBus, outBus, grainGrp, params;
		^super.newCopyArgs(server, synthId, synthfxname, buffer, ptrBus, outBus, grainGrp, params)
	}

	init {
		this.initUGens;

		gainParam = params.getParam("%%".format(\Gain, synthId).asSymbol);
		grainDensityParam = params.getParam("%%".format(\GrainDensity, synthId).asSymbol);
		grainSizeParam = params.getParam("%%".format(\GrainSize, synthId).asSymbol);
		delayParam = params.getParam("%%".format(\GrainDelay, synthId).asSymbol);
		pitchParam = params.getParam("%%".format(\GrainPitch, synthId).asSymbol);
		stereoWidthParam = params.getParam("%%".format(\GrainStereoWidth, synthId).asSymbol);
		onStateParam = params.getParam("%%".format(\OnState, synthId).asSymbol);
		syncModeParam = params.getParam("%%".format(\SyncMode, synthId).asSymbol);
		masterTempoParam = params.getParam("%%".format(\Tempo, "Master").asSymbol);

		gainParam.addListener(\UpdateSynth, { AppClock.sched(0,{ synthfx.set(\amp, gainParam.get); }); });
		grainDensityParam.addListener(\UpdateSynth, {
			AppClock.sched(0,{
				synthfx.set(\dens, this.calcGrainDensity);
			});
		});
		grainSizeParam.addListener(\UpdateSynth, {
			AppClock.sched(0,{
				synthfx.set(\baseDur, this.calcGrainSize);
			});
		});
		delayParam.addListener(\UpdateSynth, {
			AppClock.sched(0,{
				synthfx.set(\ptrSampleDelay, this.calcDelay);
			});
		});
		pitchParam.addListener(\UpdateSynth, {
			AppClock.sched(0, {
				synthfx.set(\rate, pitchParam.get.midiratio);
			});
		});
		stereoWidthParam.addListener(\UpdateSynth, { AppClock.sched(0,{ synthfx.set(\pan, stereoWidthParam.get); }); });
		onStateParam.addListener(\UpdateSynth, { AppClock.sched(0, {
				if (onStateParam.get == 0) {
					synthfx = Synth(synthfxname, [
						\amp, gainParam.get,
						\buf, buffer,
						\out, outBus,
						\atk, 1,
						\rel, 1,
						\gate, 1,
						\sync, syncModeParam.get,
						\dens, this.calcGrainDensity,
						\baseDur, this.calcGrainSize,
						\durRand, 1,
						\rate, pitchParam.get.midiratio,
						\rateRand, 0.midiratio,
						\pan, stereoWidthParam.get,
						\panRand, 0.0,
						\grainEnv, -1,
						\ptrBus, ptrBus,
						\ptrSampleDelay, this.calcDelay,
						\ptrRandomSamples, 0,
						\minPtrDelay, 0
					], grainGrp);
				} {
					synthfx.free;
				}
			});
		});
		syncModeParam.addListener(\UpdateSynth, { AppClock.sched(0,{ synthfx.set(\sync, syncModeParam.get); }); });
		masterTempoParam.addListener("%%".format("UpdateSynth", synthId).asSymbol, {
			AppClock.sched(0.0,{
				if ((syncModeParam.get == 1) && (onStateParam.get == 0)) {
					synthfx.set(\dens, this.calcGrainDensity);
					synthfx.set(\baseDur, this.calcGrainSize);
					synthfx.set(\ptrSampleDelay, this.calcDelay);
				};
			});
		});
	}

	calcGrainDensity { ^grainDensityParam.get*this.getBeatsPerSecond }

	calcGrainSize { ^grainSizeParam.get*this.getBeatsPerSecond }

	calcDelay { ^server.sampleRate*delayParam.get*this.getBeatsPerSecond }

	getBeatsPerSecond {
		var bps;
		if (syncModeParam.get == 0) {
			bps = 1;
		} {
			bps = masterTempoParam.get/60;
		};
		^bps
	}

	initUGens {
		SynthDef.new(\granularfx, {
		    arg amp=0.5, buf=0, out=0,
		    atk=1, rel=1, gate=1,
		    sync=0, dens=40,
		    baseDur=0.05, durRand=1,
		    rate=1, rateRand=1,
		    pan=0, panRand=0,
		    grainEnv=(-1), ptrBus=0, ptrSampleDelay=20000,
		    ptrRandSamples=5000, minPtrDelay=1000,
		    tempo=120, tempofactor=1,
		    granulationTriggerEnvelope=Env.asr;


		    var sig, env, densCtrl, durCtrl, rateCtrl, panCtrl,
		    origPtr, ptr, ptrRand, totalDelay, maxGrainDur;

		    env = EnvGen.kr(
		        Env.asr(attackTime:atk, sustainLevel:1,releaseTime:rel),
		        gate:gate,
		        doneAction:2
		    );

		    densCtrl = Select.ar(sync, [Dust.ar(dens), Impulse.ar(dens), Impulse.ar(tempofactor*tempo/60)+Dust.ar(dens)]);
		    durCtrl = baseDur * LFNoise1.ar(100).exprange(1/durRand, durRand);
		    rateCtrl = rate * LFNoise1.ar(100).exprange(1/rateRand, rateRand);
		    panCtrl = pan + LFNoise1.kr(100).bipolar(panRand);

		    ptrRand = LFNoise1.ar(100).bipolar(ptrRandSamples);
		    totalDelay = max(ptrSampleDelay - ptrRand, minPtrDelay);

		    origPtr = In.ar(ptrBus, 1);
		    ptr = origPtr - totalDelay;
		    ptr = ptr / BufFrames.kr(buf);

		    // maxGrainDur = (totalDelay / rateCtrl) / SampleRate.ir;
		    // durCtrl = min(durCtrl, maxGrainDur);

		    sig = GrainBuf.ar(
		        numChannels:2,
		        trigger:densCtrl,
		        dur:durCtrl,
		        sndbuf:buf,
		        rate:rateCtrl,
		        pos:ptr,
		        interp:2,
		        pan:panCtrl,
		        envbufnum:grainEnv
		    );

		    sig = (sig * env * amp);
		    Out.ar(out, sig);
		}).send(server);
	}
}

GranulatorMasterUI {
	// master layout
	var server, pluginTitle, version, params;
	var pluginTitleLabel, masterLabel,
	inputDeviceLabel, outputDeviceLabel,
	masterGainSlider, masterGainText,
	masterMixSlider, masterMixText,
	masterTempoLayout, masterTempoSlider, nbTempo,
	inputDevicePopUp, outputDevicePopUp,
	loadingText;

	var masterGainParam, masterMixParam,
	masterTempoParam;

	*new {
		arg server, pluginTitle, version, params;
		^super.newCopyArgs(server, pluginTitle, version, params)
	}

	init {
		arg setUp, tearDown;

		// Get params
		masterGainParam = params.getParam("%%".format(\Gain, "Master").asSymbol);
		masterMixParam = params.getParam("%%".format(\Mix, "Master").asSymbol);
		masterTempoParam = params.getParam("%%".format(\Tempo, "Master").asSymbol);

		pluginTitleLabel = StaticText().string_(pluginTitle).font_(Font("Helvetica",18, true));
		inputDeviceLabel = StaticText().string_("Input Device").font_(Font("Helvetica", 14, bold:true));
		outputDeviceLabel = StaticText().string_("Output Device").font_(Font("Helvetica", 14, bold:true));
		masterLabel = StaticText().string_("Master").font_(Font("Helvetica", 16, bold:true));
		masterGainSlider = Slider().value_(masterGainParam.get)
			.action_({
				arg l;
				masterGainParam.setRaw(l.value);
			}).value_(masterGainParam.getRaw)
			.orientation_(\vertical);
		masterGainText = StaticText().string_("Gain").align_(\center);
		masterMixSlider = Slider().value_(masterMixParam.get)
			.action_({
				arg l;
				masterMixParam.setRaw(l.value);
			}).value_(masterMixParam.getRaw)
			.orientation_(\vertical);
		masterMixText = StaticText().string_("Dry/Wet").align_(\center);
		loadingText = StaticText().align_(\center);
		inputDevicePopUp = PopUpMenu().items_(ServerOptions.inDevices).font_(Font("Helvetica",12));
		outputDevicePopUp = PopUpMenu().items_(ServerOptions.outDevices).font_(Font("Helvetica",12));
		masterTempoLayout = VLayout(
			HLayout(
				StaticText().string_("Tempo"),
				StaticText().string_("bpm").align_(\right),
			),
			HLayout(
				masterTempoSlider = Slider().orientation_(\horizontal)
					.action_({
						arg l;
						masterTempoParam.setRaw(l.value);
					}).value_(masterTempoParam.getRaw),
					nbTempo = NumberBox().maxWidth_(40).action_({
						arg v;
						masterTempoParam.set(v.value);
					}).value_(masterTempoParam.get)
			)
		);
		masterTempoParam.addListenerOnRaw(\WatchSlider, { AppClock.sched(0,{ nbTempo.value = masterTempoParam.get; }); });
		masterTempoParam.addListener(\WatchNb, { AppClock.sched(0,{ masterTempoSlider.value = masterTempoParam.getRaw; }); });

		GranulatorPreferences.initClass;

		inputDevicePopUp.action = {
			arg c;
			this.setInputDevice(server, c.item);
			{this.runBootSequence(server, setUp, tearDown)}.defer(0.1);
		};

		outputDevicePopUp.action = {
			arg c;
			this.setOutputDevice(server, c.item);
			{this.runBootSequence(server, setUp, tearDown)}.defer(0.1);
		};
	}

	refreshAudioDevices {
		arg setUp, tearDown;
		this.setPreferredDevices(server);
		this.setDefaultAudioDevices(server);
		// Defer, so that changes in the audio devices
		// are clearly reflected before we boot
		{this.runBootSequence(server, setUp, tearDown)}.defer(0.1);
	}

	runBootSequence {
		arg server, setUp, tearDown;

		this.disableElements;
		loadingText.string_("Setting up audio device...");
		tearDown.value;
		server.reboot();
		server.waitForBoot(
			onComplete: {
				setUp.value;
				loadingText.string_("Ready!");
				this.enableElements;
				1.wait;
				loadingText.string_("");
			},
			onFailure:{
				loadingText.string_("Something went wrong while setting up audio device...");
			}
		);
	}

	setInputDevice {
		arg server, device;
		server.options.inDevice_(device);
		GranulatorPreferences.savePref("inDevice", [device]);
	}

	setOutputDevice {
		arg server, device;
		server.options.outDevice_(device);
		GranulatorPreferences.savePref("outDevice", [device]);
	}

	setPreferredDevices {
		arg server;
		var preferredInDevice = GranulatorPreferences.loadPref("inDevice")[0];
		var preferredOutDevice = GranulatorPreferences.loadPref("outDevice")[0];
		var devicesChanged = false;

		if (preferredInDevice.size > 0) {
			(0,1..ServerOptions.inDevices.size-1).do({
				arg i;
				if (preferredInDevice.compare(ServerOptions.inDevices[i]) == 0, {
					server.options.inDevice_(preferredInDevice);
					inputDevicePopUp.value_(i);
				}, {});
			});
		};

		if (preferredOutDevice.size > 0) {
			(0,1..ServerOptions.outDevices.size-1).do({
				arg i;
				if (preferredOutDevice.compare(ServerOptions.outDevices[i]) == 0, {
					server.options.outDevice_(preferredOutDevice);
					outputDevicePopUp.value_(i);
				}, {});
			});
		};
	}

	setDefaultAudioDevices {
		arg server;

		postf("InDevice: %\n", server.options.inDevice);
		postf("OutDevice: %\n", server.options.outDevice);

		if (server.options.inDevice == nil, {
			server.options.inDevice_(ServerOptions.inDevices[0]);
		}, { });
		if (server.options.outDevice == nil, {
			server.options.outDevice_(ServerOptions.outDevices[0]);
		}, { });

		(0,1..ServerOptions.inDevices.size-1).do({
			arg i;
			if (server.options.inDevice.compare(ServerOptions.inDevices[i]) == 0, {
				inputDevicePopUp.value_(i);
			}, {});
		});

		(0,1..ServerOptions.outDevices.size-1).do({
			arg i;
			if (server.options.outDevice.compare(ServerOptions.outDevices[i]) == 0, {
				outputDevicePopUp.value_(i);
			}, {});
		});
	}

	getMasterLayout {
		^VLayout(
			pluginTitleLabel,
			VLayout(
				inputDeviceLabel,
				inputDevicePopUp,
				outputDeviceLabel,
				outputDevicePopUp,
				loadingText
			).margins_([0,20,0,0]),
			VLayout(
				masterLabel,
				masterTempoLayout,
				HLayout(
					VLayout(
						masterGainSlider,
						masterGainText,
					),
					VLayout(
						masterMixSlider,
						masterMixText
					)
				)
			).margins_([0,20,0,0])
		)
	}

	disableElements {
		masterGainSlider.enabled_(0);
		masterMixSlider.enabled_(0);
		masterTempoSlider.enabled_(0);
	}

	enableElements {
		masterGainSlider.enabled_(1);
		masterMixSlider.enabled_(1);
		masterTempoSlider.enabled_(1);
	}
}

GranulatorUI {
	var id, <title, params;
	var titleLabel,
	onButton, onStateParam,
	syncButton, syncModeParam,
	gainLayout, sliderGain, nbGain, gainParam,
	grainDensityLayout, grainDensityUnitLabel, sliderGrainDensity, nbGrainDensity, grainDensityParam,
	grainSizeLayout, grainSizeUnitLabel, sliderGrainSize, nbGrainSize, grainSizeParam, grainSizeParam,
	delayLayout, delayUnitLabel, sliderDelay, nbDelay, delayParam,
	pitchLayout, sliderPitch, nbPitch, pitchParam,
	stereoWidthLayout, sliderStereoWidth, nbStereoWidth, stereoWidthParam;

   	*new {
		arg id, title, params;
	   	^super.newCopyArgs(id, title, params)
   	}

	init {
		gainParam = params.getParam("%%".format(\Gain, id).asSymbol);
		grainDensityParam = params.getParam("%%".format(\GrainDensity, id).asSymbol);
		grainSizeParam = params.getParam("%%".format(\GrainSize, id).asSymbol);
		delayParam = params.getParam("%%".format(\GrainDelay, id).asSymbol);
		pitchParam = params.getParam("%%".format(\GrainPitch, id).asSymbol);
		stereoWidthParam = params.getParam("%%".format(\GrainStereoWidth, id).asSymbol);

		onStateParam = params.getParam("%%".format(\OnState, id).asSymbol);
		syncModeParam = params.getParam("%%".format(\SyncMode, id).asSymbol);

		titleLabel = StaticText().string_(title).font_(Font("Helvetica", 16, bold:true));

		onButton = Button()
			.states_([
				["Off", Color.gray(0.2), Color.gray(0.8)],
				["On", Color.gray(0.2), Color.grey(0.9)]
			])
			.mouseDownAction_({
				arg state;
				if (state.value == 0) {
					this.enableElements;
				} {
					this.disableElements;
				};

				onStateParam.set(state.value);
			})
			.value_(onStateParam.get)
			.minHeight_(20)
			.minWidth_(70);

		syncButton = Button()
			.states_([
				["Sync off", Color.gray(0.2), Color.gray(0.8)],
				["Sync on", Color.gray(0.2), Color.gray(0.9)]
			])
			.mouseDownAction_({
				arg state;
				syncModeParam.set(1-state.value);
				this.updateSyncLabels;
			})
			.value_(syncModeParam.get);

		gainLayout = VLayout(
			HLayout(
				StaticText().string_("Gain"),
				StaticText().string_("").align_(\right),
			),
			HLayout(
				sliderGain = Slider().orientation_(\horizontal).action_({
					arg l;
					gainParam.setRaw(l.value);
				}).value_(gainParam.getRaw),
				nbGain = NumberBox().maxWidth_(40).action_({
					arg v;
					gainParam.set(v.value);
				}).value_(gainParam.get)
			)
		);
		gainParam.addListenerOnRaw(\WatchSlider, { AppClock.sched(0,{ nbGain.value = gainParam.get; }); });
		gainParam.addListener(\WatchNb, { AppClock.sched(0,{ sliderGain.value = gainParam.getRaw; }); });

		grainDensityLayout = HLayout(
			VLayout(
				HLayout(
					StaticText().string_("Grain Density"),
					grainDensityUnitLabel = StaticText().string_("grains/sec").align_(\right),
				),
				HLayout(
					sliderGrainDensity = Slider().orientation_(\horizontal).action_({
						arg l;
						grainDensityParam.setRaw(l.value);
					}).value_(grainDensityParam.getRaw),
					nbGrainDensity = NumberBox().maxWidth_(40).action_({
						arg v;
						grainDensityParam.set(v.value);
					}).value_(grainDensityParam.get);
				)
			)
		);
		grainDensityParam.addListenerOnRaw(\WatchSlider, { AppClock.sched(0,{ nbGrainDensity.value = grainDensityParam.get; }); });
		grainDensityParam.addListener(\WatchNb, { AppClock.sched(0,{ sliderGrainDensity.value = grainDensityParam.getRaw; }); });

		grainSizeLayout = VLayout(
			HLayout(
				StaticText().string_("Grain Size"),
				grainSizeUnitLabel = StaticText().string_("sec").align_(\right),
			),
			HLayout(
				sliderGrainSize = Slider().orientation_(\horizontal).action_({
					arg l;
					grainSizeParam.setRaw(l.value);
				}).value_(grainSizeParam.getRaw),
				nbGrainSize = NumberBox().maxWidth_(40).action_({
					arg v;
					grainSizeParam.set(v.value);
				}).value_(grainSizeParam.get)
			)
		);
		grainSizeParam.addListenerOnRaw(\WatchSlider, { AppClock.sched(0,{ nbGrainSize.value = grainSizeParam.get; }); });
		grainSizeParam.addListener(\WatchNb, { AppClock.sched(0,{ sliderGrainSize.value = grainSizeParam.getRaw; }); });

		delayLayout = VLayout(
			HLayout(
				StaticText().string_("Start position delay"),
				delayUnitLabel = StaticText().string_("sec").align_(\right),
			),
			HLayout(
				sliderDelay = Slider().orientation_(\horizontal).action_({
					arg l;
					delayParam.setRaw(l.value);
				}).value_(delayParam.getRaw),
				nbDelay = NumberBox().maxWidth_(40).action_({
					arg v;
					delayParam.set(v.value);
				}).value_(delayParam.get)
			)
		);
		delayParam.addListenerOnRaw(\WatchSlider, { AppClock.sched(0,{ nbDelay.value = delayParam.get; }); });
		delayParam.addListener(\WatchNb, { AppClock.sched(0,{ sliderDelay.value = delayParam.getRaw; }); });

		pitchLayout = VLayout(
			HLayout(
				StaticText().string_("Pitch"),
				StaticText().string_("semitones").align_(\right),
			),
			HLayout(
				sliderPitch = Slider().orientation_(\horizontal).action_({
					arg l;
					pitchParam.setRaw(l.value);
				}).value_(delayParam.getRaw),
				nbPitch = NumberBox().maxWidth_(40).action_({
					arg v;
					pitchParam.set(v.value);
				}).value_(delayParam.get)
			)
		);
		pitchParam.addListenerOnRaw(\WatchSlider, { AppClock.sched(0,{ nbPitch.value = pitchParam.get; }); });
		pitchParam.addListener(\WatchNb, { AppClock.sched(0,{ sliderPitch.value = pitchParam.getRaw; }); });

		stereoWidthLayout = VLayout(
			HLayout(
				StaticText().string_("Width (stereo)"),
				StaticText().string_("").align_(\right),
			),
			HLayout(
				sliderStereoWidth = Slider().orientation_(\horizontal).action_({
					arg l;
					stereoWidthParam.setRaw(l.value);
				}).value_(stereoWidthParam.getRaw),
				nbStereoWidth = NumberBox().maxWidth_(40).action_({
					arg v;
					stereoWidthParam.set(v.value);
				}).value_(stereoWidthParam.get)
			)
		);
		stereoWidthParam.addListenerOnRaw(\WatchSlider, { AppClock.sched(0,{ nbStereoWidth.value = stereoWidthParam.get; }); });
		stereoWidthParam.addListener(\WatchNb, { AppClock.sched(0,{ sliderStereoWidth.value = stereoWidthParam.getRaw; }); });

		this.turnOff;
		this.updateSyncLabels;
	}

	updateSyncLabels {
		if (syncModeParam.get == 1) {
			grainDensityUnitLabel.string_("grains/beat");
			grainSizeUnitLabel.string_("beats");
			delayUnitLabel.string_("beats");
		} {
			grainDensityUnitLabel.string_("grains/sec");
			grainSizeUnitLabel.string_("sec");
			delayUnitLabel.string_("sec");
		};
	}

	getLayout {
		^VLayout(
			titleLabel,
			onButton,
			syncButton,
			gainLayout,
			grainDensityLayout,
			grainSizeLayout,
			delayLayout,
			pitchLayout,
			stereoWidthLayout
		)
	}

	turnOn {
		onButton.valueAction_(1);
		this.enableElements;
		^onButton.value;
	}

	turnOff {
		onButton.valueAction_(0);
		this.disableElements;
		^onButton.value;
	}

	disableButton {
		onButton.enabled_(0);
		onButton.states_([
			["Off", Color.gray(0.2, 0.8), Color.gray(0.8, 0.5)],
			["On", Color.gray(0.2, 0.8), Color.grey(0.9, 0.5)]
		]);
	}

	enableButton {
		onButton.enabled_(1);
		onButton.states_([
			["Off", Color.gray(0.2), Color.gray(0.8)],
			["On", Color.gray(0.2), Color.grey(0.9)]
		]);
	}

	disableElements {
		syncButton.enabled_(0);
		syncButton.states_([
			["Sync off", Color.gray(0.2, 0.8), Color.gray(0.8, 0.5)],
			["Sync on", Color.gray(0.2, 0.8), Color.grey(0.9, 0.5)]
		]).valueAction_(syncModeParam.get);
		sliderGain.enabled_(0);
		nbGain.enabled_(0);
		sliderGrainDensity.enabled_(0);
		nbGrainDensity.enabled_(0);
		sliderGrainSize.enabled_(0);
		nbGrainSize.enabled_(0);
		sliderDelay.enabled_(0);
		nbDelay.enabled_(0);
		sliderPitch.enabled_(0);
		nbPitch.enabled_(0);
		sliderStereoWidth.enabled_(0);
		nbStereoWidth.enabled_(0);
	}

	enableElements {
		syncButton.enabled_(1);
		syncButton.states_([
			["Sync off", Color.gray(0.2), Color.gray(0.8)],
			["Sync on", Color.gray(0.2), Color.grey(0.9)]
		]).valueAction_(syncModeParam.get);
		sliderGain.enabled_(1);
		nbGain.enabled_(1);
		sliderGrainDensity.enabled_(1);
		nbGrainDensity.enabled_(1);
		sliderGrainSize.enabled_(1);
		nbGrainSize.enabled_(1);
		sliderDelay.enabled_(1);
		nbDelay.enabled_(1);
		sliderPitch.enabled_(1);
		nbPitch.enabled_(1);
		sliderStereoWidth.enabled_(1);
		nbStereoWidth.enabled_(1);
	}
}

GranulatorParam {
	var name, id, minValue, maxValue, step,
	default, units, listeners;

	var value, cSpec;
	var <setDefName, <setRawDefName,
	<setPathName, <setRawPathName,
	<broadcastOscPathName, <broadcastRawOscPathName;

	var defaultAction, defaultRawValueAction;
	classvar net;

	*new {
		arg name, id, minValue, maxValue, step, default, units;
		^super.newCopyArgs(name, id, minValue, maxValue, step, default, units, []);
	}

	init {
		cSpec = ControlSpec(
			minValue, maxValue,
			step: step,
			default: default,
			units: units
		);
		value = cSpec.unmap(default);

		net = NetAddr("127.0.0.1", NetAddr.langPort);
		setDefName = "%%OSCsetDef".format(name, id).asSymbol;
		setRawDefName = "%%OSCsetRawDef".format(name, id).asSymbol;
		setPathName = "set%%".format(name, id).asSymbol;
		setRawPathName = "setRaw%%".format(name, id).asSymbol;
		broadcastOscPathName = "broadcast%%".format(name, id).asSymbol;
		broadcastRawOscPathName = "broadcastRaw%%".format(name, id).asSymbol;
		defaultAction = {|msg| this.set(msg[1])};
		defaultRawValueAction = {|msg| this.setRaw(msg[1])};
		this.prepareOSCComm;
		this.broadcast;
	}

	broadcast {
		net.sendMsg(broadcastOscPathName, this.get);
		net.sendMsg(broadcastRawOscPathName, this.getRaw);
	}

	setRaw {
		arg v;
		value = v;
		this.broadcast;
	}

	set {
		arg v;
		value = cSpec.unmap(v);
		this.broadcast;
	}

	get { ^cSpec.map(value) }
	getRaw { ^value }
	getBounds { ^[minValue, maxValue] }

	// Setup listeners
	addListener {
		arg listenerId, action;
		var defName = "%%%".format(listenerId, name, id).asSymbol;
		OSCdef(defName, action, broadcastOscPathName);
		listeners.add(defName);
	}

	addListenerOnRaw {
		arg listenerId, action;
		var defName = "%%%".format(listenerId, name, id).asSymbol;
		OSCdef(defName, action, broadcastRawOscPathName);
		listeners.add(defName);
	}

	clearListeners {
		listeners.do({
			arg item;
			OSCdef(item).free;
		});
		listeners = [];
	}

	// Set parameter using OSC messages
	prepareOSCComm {
		OSCdef(setDefName, defaultAction, setPathName, net);
		OSCdef(setRawDefName, defaultRawValueAction, setRawPathName, net);
	}

	addAction {
		arg action;
		OSCdef(setDefName).add(action);
	}
	addRawAction {
		arg action;
		OSCdef(setRawDefName).add(action);
	}

	resetActions {
		this.clearActions;
		this.prepareOSCComm;
	}

	clearActions {
		OSCdef(setDefName).free;
		OSCdef(setRawDefName).free;
	}
}

GranulatorParameterStore {
	var <params;

	*new {
		^super.newCopyArgs(())
	}

	addParam {
		arg name, id, minValue, maxValue, step, default, units;
		var uniqueId = "%%".format(name, id).asSymbol;
		var param = GranulatorParam.new(name, id, minValue, maxValue, step, default, units);
		param.init.value;
		params.put(uniqueId, param);
		^param
	}

	getParam {
		arg key;
		^params.at(key)
	}

	clear {
		params.do(_.clearActions);
		params = ();
	}
}
