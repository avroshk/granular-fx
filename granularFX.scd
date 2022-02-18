Server.killAll;
ServerOptions.devices;

/*
 * Reference: SuperCollider Tutorial: 26. Granular Synthesis, Part II by Eli Fieldsteel
 * https://youtu.be/MnD8stNB5tE
*/

// BlackHole 16ch, MacBook Pro Microphone, MacBook Pro Speakers, PLT V6200 Series, PLT V6200 Series, ZoomAudioD, External Headphones

(
s.options.inDevice_("BlackHole 16ch");
s.options.outDevice_("MacBook Pro Speakers");
s.reboot;
)


(
s.boot;
s.meter;
s.scope;
s.plotTree;
)

(
b = Buffer.alloc(
	s,
	numFrames:s.sampleRate * 5,
	numChannels:1
); // 5 seconds long buffer @ 48kHz


~micBus = Bus.audio(s, 1);
~ptrBus = Bus.audio(s, 1);

~micGrp = Group.new;
~ptrGrp = Group.after(~micGrp);
~recGrp = Group.after(~ptrGrp);
~grainGrp = Group.after(~recGrp);
)

(
SynthDef.new(\mic, {
	arg in=0, out=0, amp=1;
	var sig;
	sig = SoundIn.ar(bus:in) * amp;
	Out.ar(bus:out,channelsArray:sig);
}).add;

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
}).add;

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
}).add;

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
	granulationTriggerEnvelope=Env.a;


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
}).add;

Synth(\mic, [\in, 0, \out, ~micBus], ~micGrp);
Synth(\ptr, [\buf, b, \out, ~ptrBus], ~ptrGrp);
Synth(\rec, [\ptrIn, ~ptrBus, \micIn, ~micBus, \buf, b], ~recGrp);
)

(
~g = Synth(\granularfx, [
	\amp, 1.0,
	\buf, b,
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
	\ptrBus, ~ptrBus,
	\ptrSampleDelay, s.sampleRate/3,
	\ptrRandomSamples, s.sampleRate/6,
	\minPtrDelay, 1000,
	\in, 0,
	\mix, 1.0
], ~grainGrp);
)

)

// Preset 1: Drum and Bass Down 170 bpm | Piano Warped G Minor 98 bpm
~g.set(\amp, 1, \sync, 1, \dens, 16, \baseDur, 0.05, \durRand, 1.5, \rate, 1, \mix, 0.75, \pan, 0.0, \panRand, 0.5);

// Preset 2: Drum and Bass Down 170 bpm | Piano Warped G Minor 98 bpm
~g.set(\amp, 1, \sync, 1, \dens, 128, \baseDur, 0.05, \durRand, 0.5, \rate, (12).midiratio, \mix, 0.75, \pan, 0.0, \panRand, 0.5);

~g.set(\amp, 1, \sync, 2, \dens, 8, \baseDur, 0.2, \durRand, 1, \rate, (0).midiratio, \mix, 0.0, \pan, 0.0, \panRand, 0.5);

~g.set(\tempo, 170, \tempofactor, 1);
~g.set(\ptrSampleDelay, 1);
~g.set(\ptrRandomSamples, 0);
~g.set(\minPtrDelay, 0);
~g.set(\gate, 0);

~g.free;
s.defaultGroup.deepFree;

b.play;
b.plot;

{ Impulse.kr(16)*0.9 }.plot(1);
{ Dust.kr(16)*0.9 }.plot(1);
{ Impulse.kr(16)*0.9+Dust.kr(16)*0.9 }.plot(1);
