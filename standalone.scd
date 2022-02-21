// https://lukaprincic.si/development-log/supercollider-granulator-a-gui

// Setup granularFX UI Classes
(
var classDefFile = thisProcess.nowExecutingPath.dirname +/+ "granularFXguiClasses.sc";
var target = Platform.userExtensionDir +/+ "local" +/+ "granularFXguiClasses.sc";

if (File.exists(target), { File.delete(target);}, { "Clean workspace".postln; });
if (File.exists(classDefFile)) { File.copy(classDefFile, target); }

)

(
s.waitForBoot({
	var inBus=0, outBus=0;
	var bufferLength=5;
	var granulatorUI1, granulatorUI2, granulatorUI3;
	var gSynth1, gSynth2, gSynth3;
	var setUp, tearDown, gSetup;

	// Initialize granulators
	granulatorUI1 = GranulatorUI("Granulator1");
	granulatorUI2 = GranulatorUI("Granulator2");
	granulatorUI3 = GranulatorUI("Granulator3");
	granulatorUI1.init;
	granulatorUI2.init;
	granulatorUI3.init;

	setUp = {

		// Create Buses & Groups to manage the signal chain
		gSetup = GranulatorSetup.new(s, bufferLength, inBus, outBus);
		gSetup.init;

		gSetup.setMasterGainAction(GranulatorMasterUI.masterGainSlider);

		gSynth1 = GranulatorSynth.new(s, \granularfx, gSetup.buffer, gSetup.ptrBus, gSetup.masterBus, gSetup.grainGrp);
		gSynth2 = GranulatorSynth.new(s, \granularfx, gSetup.buffer, gSetup.ptrBus, gSetup.masterBus, gSetup.grainGrp);
		gSynth3 = GranulatorSynth.new(s, \granularfx, gSetup.buffer, gSetup.ptrBus, gSetup.masterBus, gSetup.grainGrp);
		gSynth1.init;
		gSynth2.init;
		gSynth3.init;

		gSynth1.setOnButtonAction(granulatorUI1);
		gSynth2.setOnButtonAction(granulatorUI2);
		gSynth3.setOnButtonAction(granulatorUI3);

		gSynth1.setMixAction(granulatorUI1.cSpecMix, granulatorUI1.sliderMix, granulatorUI1.nbMix);
		gSynth1.setGainAction(granulatorUI1.cSpecGain, granulatorUI1.sliderGain, granulatorUI1.nbGain);
		gSynth1.setGrainDensityAction(granulatorUI1.cSpecGrainDensity, granulatorUI1.sliderGrainDensity, granulatorUI1.nbGrainDensity);
		gSynth1.setGrainSizeAction(granulatorUI1.cSpecGrainSize, granulatorUI1.sliderGrainSize, granulatorUI1.nbGrainSize);
		gSynth1.setDelayAction(granulatorUI1.cSpecDelay, granulatorUI1.sliderDelay, granulatorUI1.nbDelay);
		gSynth1.setPitchAction(granulatorUI1.cSpecPitch, granulatorUI1.sliderPitch, granulatorUI1.nbPitch);
		gSynth1.setStereoWidthAction(granulatorUI1.cSpecStereoWidth, granulatorUI1.sliderStereoWidth, granulatorUI1.nbStereoWidth);

		gSynth2.setMixAction(granulatorUI2.cSpecMix, granulatorUI2.sliderMix, granulatorUI2.nbMix);
		gSynth2.setGainAction(granulatorUI2.cSpecGain, granulatorUI2.sliderGain, granulatorUI2.nbGain);
		gSynth2.setGrainDensityAction(granulatorUI2.cSpecGrainDensity, granulatorUI2.sliderGrainDensity, granulatorUI2.nbGrainDensity);
		gSynth2.setGrainSizeAction(granulatorUI2.cSpecGrainSize, granulatorUI2.sliderGrainSize, granulatorUI2.nbGrainSize);
		gSynth2.setDelayAction(granulatorUI2.cSpecDelay, granulatorUI2.sliderDelay, granulatorUI2.nbDelay);
		gSynth2.setPitchAction(granulatorUI2.cSpecPitch, granulatorUI2.sliderPitch, granulatorUI2.nbPitch);
		gSynth2.setStereoWidthAction(granulatorUI2.cSpecStereoWidth, granulatorUI2.sliderStereoWidth, granulatorUI2.nbStereoWidth);

		gSynth3.setMixAction(granulatorUI3.cSpecMix, granulatorUI3.sliderMix, granulatorUI3.nbMix);
		gSynth3.setGainAction(granulatorUI3.cSpecGain, granulatorUI3.sliderGain, granulatorUI3.nbGain);
		gSynth3.setGrainDensityAction(granulatorUI3.cSpecGrainDensity, granulatorUI3.sliderGrainDensity, granulatorUI3.nbGrainDensity);
		gSynth3.setGrainSizeAction(granulatorUI3.cSpecGrainSize, granulatorUI3.sliderGrainSize, granulatorUI3.nbGrainSize);
		gSynth3.setDelayAction(granulatorUI3.cSpecDelay, granulatorUI3.sliderDelay, granulatorUI3.nbDelay);
		gSynth3.setPitchAction(granulatorUI3.cSpecPitch, granulatorUI3.sliderPitch, granulatorUI3.nbPitch);
		gSynth3.setStereoWidthAction(granulatorUI3.cSpecStereoWidth, granulatorUI3.sliderStereoWidth, granulatorUI3.nbStereoWidth);

		granulatorUI1.applySynthDefaults;
		granulatorUI2.applySynthDefaults;
		granulatorUI3.applySynthDefaults;

		granulatorUI1.enableButton;
		granulatorUI2.enableButton;
		granulatorUI3.enableButton;
	};

	tearDown = {
		granulatorUI1.disableButton;
		granulatorUI2.disableButton;

		granulatorUI3.disableButton;
		granulatorUI1.turnOff;
		granulatorUI2.turnOff;
		granulatorUI3.turnOff;

		gSetup.freeAll;
	};

	setUp.value;

	// UI
	QtGUI.palette = QPalette.dark;
	~gwin = Window.new("granularFX", Rect(left:20, top:20, width: 1000, height: 400)).alpha_(0.98);
	~gwin.background = Color.gray(0.3,1);

	GranulatorMasterUI.masterInit(s, setUp, tearDown);

	~gwin.layout = HLayout(
			GranulatorMasterUI.getMasterLayout,
			HLayout(
				granulatorUI1.getLayout.margins_([20,0,0,0]),
				granulatorUI2.getLayout.margins_([20,0,0,0]),
				granulatorUI3.getLayout.margins_([20,0,0,0]),
			)
		);

	~gwin.front;
	~gwin.alwaysOnTop_(true);
	~gwin.onClose_({
		tearDown.value;
	});
	CmdPeriod.doOnce({~gwin.close});
});

s.plotTree;
)


// [*] 1 Separate Synth elements from UI elements
// [*] 2 Disable UI elements when Synth is off
// [*] 3 Manage default parameters of the synth (save preferences in file)
// [*] 4 Handle Master Group Grain slider
// [ ] 5 Sync GRanulator outputs (check dry/wet logic)
// [*] 6 Moved Master UI elements to GUI Class GranulatorMasterUI
// [*] 7 Fix Number boxshades
// [*] Init Synth with sensible params
// [*] Disable Synth On/Off button till Audio devices are ready
