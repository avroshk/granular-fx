
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
	var server, bufferLength;
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

	*new {
		arg server, bufferLength, inBus, outBus;
		^super.newCopyArgs(server, bufferLength, inBus, outBus);
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
		micSynth = Synth(\mic, [\in, inBus, \out, micBus], micGrp);
		ptrSynth = Synth(\ptr, [\buf, buffer, \out, ptrBus], ptrGrp);
		recSynth = Synth(\rec, [\ptrIn, ptrBus, \micIn, micBus, \buf, buffer], recGrp);
		masterSynth = Synth(\master, [\dryIn, micBus, \wetIn, masterBus, \out, outBus], masterGrp);

		synths = (
			\mic: micSynth,
			\ptr: ptrSynth,
			\rec: recSynth,
			\master: masterSynth
		);

		synthIDs = synths.collect(_.nodeID);
	}

	setMasterGainAction {
		arg masterGainSlider;
		masterGainSlider.action = {
			arg l;
			masterSynth.set(\gain, l.value);
		};
	}

	setMasterMixAction {
		arg masterMixSlider;
		masterMixSlider.action = {
			arg l;
			masterSynth.set(\mix, l.value);
		};
	}

	setMasterTempoAction {
		arg cSpecTempo, sliderTempo, nbTempo;
		sliderTempo.action = {
			arg l;
			var value = cSpecTempo.map(l.value);
			nbTempo.value_(value);
		};
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
	var server, synthfxname, buffer, ptrBus, outBus, grainGrp;
	var synthfx;

	*new {
		arg server, synthfxname, buffer, ptrBus, outBus, grainGrp;
		^super.newCopyArgs(server, synthfxname, buffer, ptrBus, outBus, grainGrp)
	}

	init {
		this.initUGens;
	}

	initUGens {
		SynthDef.new(\granularfx, {
		    arg amp=0.5, buf=0, out=0,
		    atk=1, rel=1, gate=1,
		    sync=1, dens=40,
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

		    maxGrainDur = (totalDelay / rateCtrl) / SampleRate.ir;
		    durCtrl = min(durCtrl, maxGrainDur);

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

	setOnButtonAction {
		arg gui;

		if (synthfx == nil, {}, {synthfx.free;});

		gui.onButton.mouseDownAction = {
			arg state;
			if (state.value == 0) {
				synthfx = Synth(synthfxname, [
					\amp, 1,
					\buf, buffer,
					\out, outBus,
					\atk, 1,
					\rel, 1,
					\gate, 1,
					\sync, 1,
					\dens, 8,
					\baseDur, 0.05,
					\durRand, 1,
					\rate, 0.midiratio,
					\rateRand, 0.midiratio,
					\pan, 0,
					\panRand, 0.0,
					\grainEnv, -1,
					\ptrBus, ptrBus,
					\ptrSampleDelay, 0.1*server.sampleRate,
					\ptrRandomSamples, 0,
					\minPtrDelay, 0
				], grainGrp);
				gui.enableElements;
			} {
				synthfx.free;
				gui.disableElements;
			}
		};
	}

	setGainAction {
		arg cSpecGain, sliderGain, nbGain;
		sliderGain.action = {
			arg l;
			var value = cSpecGain.map(l.value);
			nbGain.value_(value);
			synthfx.set(\amp, l.value);
		};
	}

	setGrainDensityAction {
		arg cSpecGrainDensity, sliderGrainDensity, nbGrainDensity;
		sliderGrainDensity.action = {
			arg l;
			var value = cSpecGrainDensity.map(l.value);
			nbGrainDensity.value_(value);
			synthfx.set(\dens, value);
		}
	}

	setGrainSizeAction {
		arg cSpecGrainSize, sliderGrainSize, nbGrainSize;
		sliderGrainSize.action = {
			arg l;
			var value = cSpecGrainSize.map(l.value);
			nbGrainSize.value_(value);
			synthfx.set(\baseDur, value);
		}
	}

	setDelayAction {
		arg cSpecDelay, sliderDelay, nbDelay;
		sliderDelay.action = {
			arg l;
			var value = cSpecDelay.map(l.value);
			nbDelay.value_(value);
			synthfx.set(\ptrSampleDelay, server.sampleRate*value);
		}
	}

	setPitchAction {
		arg cSpecPitch, sliderPitch, nbPitch;
		sliderPitch.action = {
			arg l;
			var value = cSpecPitch.map(l.value);
			nbPitch.value_(value);
			synthfx.set(\rate, value.midiratio);
		}
	}

	setStereoWidthAction {
		arg cSpecStereoWidth, sliderStereoWidth, nbStereoWidth;
		sliderStereoWidth.action = {
			arg l;
			var value = cSpecStereoWidth.map(l.value);
			nbStereoWidth.value_(value);
			synthfx.set(\pan, value);
		}
	}
}

GranulatorMasterUI {
	// master layout
	classvar server, pluginTitle = "granularFX";
	classvar pluginTitleLabel, masterLabel,
	inputDeviceLabel, outputDeviceLabel,
	<masterGainSlider, masterGainText,
	<masterMixSlider, masterMixText,
	masterTempoLayout, <cSpecTempo, <masterTempoSlider, <nbTempo,
	inputDevicePopUp, outputDevicePopUp,
	loadingText;

	classvar defaultMasterGainValue = 0.8,
	defaultMasterTempo = 120;

	*masterInit {
		arg server, setUp, tearDown;
		pluginTitleLabel = StaticText().string_(pluginTitle).font_(Font("Helvetica",18, true));
		inputDeviceLabel = StaticText().string_("Input Device").font_(Font("Helvetica", 14, bold:true));
		outputDeviceLabel = StaticText().string_("Output Device").font_(Font("Helvetica", 14, bold:true));
		masterLabel = StaticText().string_("Master").font_(Font("Helvetica", 16, bold:true));
		masterGainSlider = Slider().value_(defaultMasterGainValue).orientation_(\vertical);
		masterGainText = StaticText().string_("Gain").align_(\center);
		masterMixSlider = Slider().orientation_(\vertical);
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
				cSpecTempo = ControlSpec(20,999,step:0.1);
				masterTempoSlider = Slider().orientation_(\horizontal),
				nbTempo = NumberBox().maxWidth_(40).action_({
					arg v;
					masterTempoSlider.valueAction = cSpecTempo.unmap(v.value);
				}).valueAction_(defaultMasterTempo)
			)
		);

		GranulatorPreferences.initClass;

		inputDevicePopUp.action = {
			arg c;
			this.setInputDevice(server, c.item);
			this.runBootSequence(server, setUp, tearDown);
		};

		outputDevicePopUp.action = {
			arg c;
			this.setOutputDevice(server, c.item);
			this.runBootSequence(server, setUp, tearDown);
		};
	}

	*refreshAudioDevices {
		arg server, setUp, tearDown;
		this.setPreferredDevices(server);
		this.setDefaultAudioDevices(server);
		// Defer, so that changes in the audio devices
		// are clearly reflected before we boot
		{this.runBootSequence(server, setUp, tearDown)}.defer(0.1);
	}

	*runBootSequence {
		arg server, setUp, tearDown;

		loadingText.string_("Setting up audio device...");
		tearDown.value;
		server.reboot();
		server.waitForBoot(
			onComplete: {
				setUp.value;
				loadingText.string_("Ready!");
				1.wait;
				loadingText.string_("");
			},
			onFailure:{
				loadingText.string_("Something went wrong while setting up audio device...");
			}
		);
	}

	*setInputDevice {
		arg server, device;
		server.options.inDevice_(device);
		GranulatorPreferences.savePref("inDevice", [device]);
	}

	*setOutputDevice {
		arg server, device;
		server.options.outDevice_(device);
		GranulatorPreferences.savePref("outDevice", [device]);
	}

	*setPreferredDevices {
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

	*setDefaultAudioDevices {
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

	*getMasterLayout {
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
}

GranulatorUI {
	var <title;
	var titleLabel, <onButton,
	gainLayout, <cSpecGain, <sliderGain, <nbGain,
	grainDensityLayout, <cSpecGrainDensity, <sliderGrainDensity, <nbGrainDensity,
	grainSizeLayout, <cSpecGrainSize, <sliderGrainSize, <nbGrainSize,
	delayLayout, <cSpecDelay, <sliderDelay, <nbDelay,
	pitchLayout, <cSpecPitch, <sliderPitch, <nbPitch,
	stereoWidthLayout, <cSpecStereoWidth, <sliderStereoWidth, <nbStereoWidth;

	// Synth Defaults
	var defaultGainValue = 1.0,
	defaultGrainDensityValue = 8,
	defaultGrainSizeValue = 0.05,
	defaultDelayValue = 0.1,
	defaultPitchValue = 0,
	defaultStereoWidthValue = 0;

   	*new {
		arg title;
	   	^super.newCopyArgs(title)
   	}

	init {
		titleLabel = StaticText().string_(title).font_(Font("Helvetica", 16, bold:true));

		onButton = Button()
			.states_([
				["Off", Color.gray(0.2), Color.gray(0.8)],
				["On", Color.gray(0.2), Color.grey(0.9)]
			])
			.minHeight_(20)
			.minWidth_(70);

		gainLayout = VLayout(
			HLayout(
				StaticText().string_("Gain"),
				StaticText().string_("").align_(\right),
			),
			HLayout(
				cSpecGain = ControlSpec(0,1,step:0.1);
				sliderGain = Slider().orientation_(\horizontal),
				nbGain = NumberBox().maxWidth_(40).action_({
					arg v;
					sliderGain.valueAction = v.value;
				}).valueAction_(defaultGainValue)
			)
		);

		grainDensityLayout = HLayout(
			VLayout(
				HLayout(
					StaticText().string_("Grain Density"),
					StaticText().string_("grains/sec").align_(\right),
				),
				HLayout(
					cSpecGrainDensity = ControlSpec(1,256,step:1);
					sliderGrainDensity = Slider().orientation_(\horizontal),
					nbGrainDensity = NumberBox().maxWidth_(40).action_({
						arg v;
						sliderGrainDensity.valueAction = cSpecGrainDensity.unmap(v.value);
					}).valueAction_(defaultGrainDensityValue);
				)
			)
		);

		grainSizeLayout = VLayout(
			HLayout(
				StaticText().string_("Grain Size"),
				StaticText().string_("sec").align_(\right),
			),
			HLayout(
				cSpecGrainSize = ControlSpec(0,3,step:0.01);
				sliderGrainSize = Slider().orientation_(\horizontal),
				nbGrainSize = NumberBox().maxWidth_(40).action_({
					arg v;
					sliderGrainSize.valueAction = cSpecGrainSize.unmap(v.value);
				}).valueAction_(defaultGrainSizeValue)
			)
		);

		delayLayout = VLayout(
			HLayout(
				StaticText().string_("Start position delay"),
				StaticText().string_("sec").align_(\right),
			),
			HLayout(
				cSpecDelay = ControlSpec(0,3,step:0.01);
				sliderDelay = Slider().orientation_(\horizontal),
				nbDelay = NumberBox().maxWidth_(40).action_({
					arg v;
					sliderDelay.valueAction = cSpecDelay.unmap(v.value);
				}).valueAction_(defaultDelayValue)
			)
		);

		pitchLayout = VLayout(
			HLayout(
				StaticText().string_("Pitch"),
				StaticText().string_("semitones").align_(\right),
			),
			HLayout(
				cSpecPitch = ControlSpec(-36,36,step:1);
				sliderPitch = Slider().orientation_(\horizontal),
				nbPitch = NumberBox().maxWidth_(40).action_({
					arg v;
					sliderPitch.valueAction = cSpecPitch.unmap(v.value);
				}).valueAction_(defaultPitchValue)
			)
		);

		stereoWidthLayout = VLayout(
			HLayout(
				StaticText().string_("Width (stereo)"),
				StaticText().string_("").align_(\right),
			),
			HLayout(
				cSpecStereoWidth = ControlSpec(-1,1,step:0.01);
				sliderStereoWidth = Slider().orientation_(\horizontal),
				nbStereoWidth = NumberBox().maxWidth_(40).action_({
					arg v;
					sliderStereoWidth.valueAction = cSpecStereoWidth.unmap(v.value);
				}).valueAction_(defaultStereoWidthValue)
			)
		);

		this.turnOff;
	}

	getLayout {
		^VLayout(
			titleLabel,
			onButton,
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

	applySynthDefaults {
		nbGain.valueAction_(defaultGainValue);
		nbGrainSize.valueAction_(defaultGrainSizeValue);
		nbGrainDensity.valueAction_(defaultGrainDensityValue);
		nbDelay.valueAction_(defaultDelayValue);
		nbPitch.valueAction_(defaultPitchValue);
		nbStereoWidth.valueAction_(defaultStereoWidthValue);
	}
}

GranulatorParam {
	var name, id, minValue, maxValue, step,
	default, units;

	var value, cSpec;
	var <setDefName, <setRawDefName,
	<setPathName, <setRawPathName,
	<broadcastOscPathName, <broadcastRawOscPathName;

	var defaultAction, defaultRawValueAction;
	classvar net;

	*new {
		arg name, id, minValue, maxValue, step, default, units;
		^super.newCopyArgs(name, id, minValue, maxValue, step, default, units)
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

	prepareOSCComm {
		OSCdef(setDefName, defaultAction, setPathName, net);
		OSCdef(setRawDefName, defaultRawValueAction, setRawPathName, net);
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

	setAction {
		arg action;
		OSCdef(setDefName).add(action);
	}
	setRawAction {
		arg action;
		OSCdef(setRawDefName).add(action);
	}

	clearActions {
		OSCdef(setDefName).free;
		OSCdef(setRawDefName).free;
		this.prepareOSCComm;
	}
}

GranulatorParameterStore {
	classvar params;

	*init {
		params = ();
	}

	*addParam {
		arg name, id, minValue, maxValue, step, default, units;
		var uniqueId = "%%".format(name, id).asSymbol;
		var param = GranulatorParam.new(name, id, minValue, maxValue, step, default, units);
		param.init.value;
		params.put(uniqueId, param);
	}

	*getParams {
		^params
	}
}
