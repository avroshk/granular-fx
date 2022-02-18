// granularFX GUI Classes
GranulatorSetup {
	var server, bufferLength;
	var <buffer;
	// Buses
	var <micBus, <ptrBus;
	var <>buses, <>busIDs;
	// Groups for managing signal flow
	var <micGrp, <ptrGrp, <recGrp, <grainGrp;
	var <>groups, <>groupIDs;
	// Synths
	var <micSynth, <ptrSynth, <recSynth;
	var <>synths, <>synthIDs;

	*new {
		arg server, bufferLength;
		^super.newCopyArgs(server, bufferLength);
	}

	init {
		buffer = Buffer.alloc(server, server.sampleRate * bufferLength,1);

		micBus = Bus.audio(server, 1);
		ptrBus = Bus.audio(server, 1);

		buses = (
			\mic: micBus,
			\ptr: ptrBus
		);

		busIDs = buses.collect(_.index);

		micGrp = Group.new;
		ptrGrp = Group.after(micGrp);
		recGrp = Group.after(ptrGrp);
		grainGrp = Group.after(recGrp);

		groups = (
			\mic: micGrp,
			\ptr: ptrGrp,
			\rec: recGrp,
			\grain: grainGrp
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
		    sig = SoundIn.ar(bus:in) * amp;
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
	}

	createSynths {
		micSynth = Synth(\mic, [\in, 0, \out, micBus], micGrp);
		ptrSynth = Synth(\ptr, [\buf, buffer, \out, ptrBus], ptrGrp);
		recSynth = Synth(\rec, [\ptrIn, ptrBus, \micIn, micBus, \buf, buffer], recGrp);

		synths = (
			\mic: micSynth,
			\ptr: ptrSynth,
			\rec: recSynth
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
		server.defaultGroup.freeAll;
		buffer.free;
	}
}

GranulatorSynth {
	var server, synthfxname, buffer, ptrBus, grainGrp;
	var synthfx;

	// Synth defaults

	*new {
		arg server, synthfxname, buffer, ptrBus, grainGrp;
		^super.newCopyArgs(server, synthfxname, buffer, ptrBus, grainGrp)
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
		    mix=0, in=0, tempo=120, tempofactor=1,
		    granulationTriggerEnvelope=Env.asr;


		    var sig, env, densCtrl, durCtrl, rateCtrl, panCtrl,
		    origPtr, ptr, ptrRand, totalDelay, maxGrainDur,
		    dry, mixedSignal;

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

		    dry = PlayBuf.ar(
		        numChannels:1,
		        bufnum:buf,
		        startPos:ptr,
		        loop:1
		    );

		    sig = (sig * env * amp);
		    dry = (dry!2 * amp);
		    mixedSignal = (mix*dry) + ((1-mix)*sig);

		    Out.ar(out, mixedSignal);
		}).send(server);
	}

	setOnButtonAction {
		arg button;

		if (synthfx == nil, {}, {synthfx.free;});

		button.mouseDownAction = {
			arg state;
			if (state.value == 0) {
				synthfx = Synth(synthfxname, [
					\amp, 1.0,
					\buf, buffer,
					\out, 0,
					\atk, 1,
					\rel, 1,
					\gate, 1,
					\sync, 1,
					\dens, 40,
					\baseDur, 0.05,
					\durRand, 1.5,
					\rate, 1,
					\rateRand, 1.midiratio,
					\pan, 0,
					\panRand, 0.5,
					\grainEnv, -1,
					\ptrBus, ptrBus,
					\ptrSampleDelay, server.sampleRate/3,
					\ptrRandomSamples, server.sampleRate/6,
					\minPtrDelay, 1000,
					\in, 0,
					\mix, 1.0
				], grainGrp);
			} {
				synthfx.free;
			}
		};
	}

	setMixAction {
		arg cSpecMix, sliderMix, nbMix;
		sliderMix.action = {
			arg l;
			var value = cSpecMix.map(l.value);
			nbMix.value_(value);
			synthfx.set(\mix, (1-l.value));
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

GranulatorUI {
	var title;
	var titleLabel, <onButton,
	mixLayout, <cSpecMix, <sliderMix, <nbMix,
	gainLayout, <cSpecGain, <sliderGain, <nbGain,
	grainDensityLayout, <cSpecGrainDensity, <sliderGrainDensity, <nbGrainDensity,
	grainSizeLayout, <cSpecGrainSize, <sliderGrainSize, <nbGrainSize,
	delayLayout, <cSpecDelay, <sliderDelay, <nbDelay,
	pitchLayout, <cSpecPitch, <sliderPitch, <nbPitch,
	stereoWidthLayout, <cSpecStereoWidth, <sliderStereoWidth, <nbStereoWidth;

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

		mixLayout = VLayout(
			HLayout(
				StaticText().string_("Dry/Wet"),
				StaticText().string_("%").align_(\right),
			),
			HLayout(
				cSpecMix = ControlSpec(0,100,step:1,units:"%");
				sliderMix = Slider().orientation_(\horizontal).value_(0);,
				nbMix = NumberBox().maxWidth_(40).action_({
					arg v;
					sliderMix.valueAction = cSpecMix.unmap(v.value);
				})
			)
		);

		gainLayout = VLayout(
			HLayout(
				StaticText().string_("Gain"),
				StaticText().string_("").align_(\right),
			),
			HLayout(
				cSpecGain = ControlSpec(0,1,step:0.1);
				sliderGain = Slider().orientation_(\horizontal).value_(0.75),
				nbGain = NumberBox().maxWidth_(40).action_({
					arg v;
					v.value.postln;
					sliderGain.valueAction = v.value;
				}).value_(0.75)
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
						v.value.postln;
						sliderGrainDensity.valueAction = cSpecGrainDensity.unmap(v.value);
					}).value_(1);
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
				sliderGrainSize = Slider().orientation_(\horizontal).value_(0),
				nbGrainSize = NumberBox().maxWidth_(40).action_({
					arg v;
					v.value.postln;
					sliderGrainSize.valueAction = cSpecGrainSize.unmap(v.value);
				}).value_(0)
			)
		);

		delayLayout = VLayout(
			HLayout(
				StaticText().string_("Start position delay"),
				StaticText().string_("sec").align_(\right),
			),
			HLayout(
				cSpecDelay = ControlSpec(0,3,step:0.01);
				sliderDelay = Slider().orientation_(\horizontal).value_(0),
				nbDelay = NumberBox().maxWidth_(40).action_({
					arg v;
					v.value.postln;
					sliderDelay.valueAction = cSpecDelay.unmap(v.value);
				}).value_(0)
			)
		);

		pitchLayout = VLayout(
			HLayout(
				StaticText().string_("Pitch"),
				StaticText().string_("semitones").align_(\right),
			),
			HLayout(
				cSpecPitch = ControlSpec(-36,36,step:1);
				sliderPitch = Slider().orientation_(\horizontal).value_(0.5),
				nbPitch = NumberBox().maxWidth_(40).action_({
					arg v;
					sliderPitch.valueAction = cSpecPitch.unmap(v.value);
				}).value_(0)
			)
		);

		stereoWidthLayout = VLayout(
			HLayout(
				StaticText().string_("Width (stereo)"),
				StaticText().string_("").align_(\right),
			),
			HLayout(
				cSpecStereoWidth = ControlSpec(-1,1,step:0.01);
				sliderStereoWidth = Slider().orientation_(\horizontal).value_(0.5),
				nbStereoWidth = NumberBox().maxWidth_(40).action_({
					arg v;
					sliderStereoWidth.valueAction = cSpecStereoWidth.unmap(v.value);
				}).value_(0)
			)
		);
	}

	getLayout {
		^VLayout(
			titleLabel,
			onButton,
			mixLayout,
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
		^onButton.value;
	}

	turnOff {
		onButton.valueAction_(0);
		^onButton.value;
	}
}
