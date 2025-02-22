(
/*
 * Synth Inspiration
 * SuperCollider Tutorial: 26. Granular Synthesis, Part II by Eli Fieldsteel
 * https://youtu.be/MnD8stNB5tE
 *
 * GUI Inspiration
 * https://lukaprincic.si/development-log/supercollider-granulator-a-gui
 */


// Hack to explicitly set in/out devices during start-up if Supercollider complains about
// the "Mismatched sample rates" between input and output devices.

var inDevice = "BlackHole 16ch";
var outDevice = "MacBook Pro Speakers";

GranulatorPreferences.savePref("inDevice", [inDevice]);
GranulatorPreferences.savePref("outDevice", [outDevice]);
s.options.inDevice = inDevice;
s.options.outDevice = outDevice;

s.waitForBoot({
	var inBus=0, outBus=0;
	var bufferLength=5;
	var masterUI, granulatorUIs, gSynths;
	var setUp, tearDown, gSetup;
	var paramsStore;

	// Synth Defaults
	var defaultMasterGainValue = 0.8,
	defaultMasterMixValue = 0,
	defaultMasterTempoValue = 120,
	defaultBeatsPerMeasure = 4,
	defaultBeatLength = 4,
	defaultGainValue = 0.75,
	defaultGrainDensityValue = 4,
	defaultGrainDensitySyncedValue = 2,
	defaultGrainSizeValue = 20,
	defaultGrainSizeSyncedValue = 2,
	defaultDelayValue = 0,
	defaultDelaySyncedValue = 4,
	defaultPitchValue = 0,
	defaultStereoWidthValue = 0;

	// Other UI Defaults
	var defaultOnState = 0,
	defaultSyncMode = 1,
	defaultReverse = 0;

	var granulators = ["Granulator1", "Granulator2", "Granulator3"];

	// Initialize Parameter store
	paramsStore = GranulatorParameterStore.new;
	paramsStore.addParam(\Gain, "Master", 0, 1, 0.01, defaultMasterGainValue, "", "Gain");
	paramsStore.addParam(\Mix, "Master", 0, 1, 0.01, defaultMasterMixValue, "", "Dry/Wet");
	paramsStore.addParam(\Tempo, "Master", 20, 999, 0.1, defaultMasterTempoValue, "bpm", "Tempo");
	paramsStore.addParam(\TimeSignatureBeatsPerMeasure, "Master", 1, 99, 1, defaultBeatsPerMeasure, "", "Beats per measure");
	paramsStore.addParam(\TimeSignatureBeatLength, "Master", 1, 16, 1, defaultBeatLength, "", "Beat Length");

	// Initialize parameters for each granulator
	granulators.do({
		arg title, i;
		var id = "G%".format(i).asSymbol;

		paramsStore.addParam(\Gain, id, 0, 1, 0.01, defaultGainValue, "", "Gain");
		paramsStore.addParam(\GrainDensity, id, 1, 256, 1, defaultGrainDensityValue, "grains/sec", "Grain Density");
		paramsStore.addParam(\GrainDensitySynced, id, 0, 20, 1, defaultGrainDensitySyncedValue, "grains/beat", "Grain Density");
		paramsStore.addParam(\GrainSize, id, 0.01, 1000, 0.01, defaultGrainSizeValue, "milliseconds", "Grain Size");
		paramsStore.addParam(\GrainSizeSynced, id, 0, 10, 1, defaultGrainSizeSyncedValue, "beatdivs", "Grain Size");
		paramsStore.addParam(\GrainDelay, id, 0, 1000, 0.01, defaultDelayValue, "milliseconds", "Grain Delay");
		paramsStore.addParam(\GrainDelaySynced, id, 0, 10, 1, defaultDelaySyncedValue, "beatdivs", "Grain Delay");
		paramsStore.addParam(\GrainPitch, id, -36, 36, 1, defaultPitchValue, "semitones", "Pitch");
		paramsStore.addParam(\GrainStereoWidth, id, -1, 1, 0.01, defaultStereoWidthValue, "", "Pan");

		paramsStore.addParam(\OnState, id, 0, 1, 1, defaultOnState, "");
		paramsStore.addParam(\SyncMode, id, 0, 1, 1, defaultSyncMode, "");
		paramsStore.addParam(\Reverse, id, 0, 1, 1, defaultReverse, "");
	});

	// Initialize Granulator UIs
	granulatorUIs = granulators.collect {|title, i| GranulatorUI("G%".format(i).asSymbol, title, paramsStore) };
	granulatorUIs.do(_.init);

	// Initialize Master UI
	masterUI = GranulatorMasterUI.new(s, "granularFX", "0.0.1", paramsStore);

	setUp = {
		// Create Buses & Groups to manage the signal chain
		gSetup = GranulatorSetup.new(s, bufferLength, paramsStore, inBus, outBus);
		gSetup.init;

		gSynths = granulators.collect {
			|title, i|
			GranulatorSynth.new(
				s,
				"G%".format(i).asSymbol,
				\granularfx,
				gSetup.buffer,
				gSetup.ptrBus,
				gSetup.masterBus,
				gSetup.grainGrp,
				paramsStore)
		};
		gSynths.do(_.init);
		granulatorUIs.do(_.enableButton);
	};

	tearDown = {
		granulatorUIs.do(_.disableButton);
		granulatorUIs.do(_.turnOff);
		gSetup.freeAll;
	};

	// UI
	QtGUI.palette = QPalette.dark;
	~gwin = Window.new("granularFX", Rect(left:20, top:20, width: 900, height: 500)).alpha_(0.98);
	~gwin.background = Color.gray(0.3,1);

	masterUI.init(setUp, tearDown);
	setUp.value;
	masterUI.refreshAudioDevices(setUp, tearDown);

	~gwin.layout = HLayout(
		masterUI.getMasterLayout,
		HLayout(
			*granulatorUIs.collect {|gui| gui.getLayout.margins_([20,0,0,0])}
		)
	);

	~gwin.front;
	~gwin.alwaysOnTop_(true);
	~gwin.onClose_({
		tearDown.value;
		paramsStore.params.do {|param| param.remove };
	});
	CmdPeriod.doOnce({~gwin.close});
});

// [*] 1 Separate Synth elements from UI elements
// [*] 2 Disable UI elements when Synth is off
// [*] 3 Manage default parameters of the synth (save preferences in file)
// [*] 4 Handle Master Group Grain slider
// [*] 6 Moved Master UI elements to GUI Class GranulatorMasterUI
// [*] 7 Fix Number boxshades
// [*] Init Synth with sensible params
// [*] Disable Synth On/Off button till Audio devices are ready
// [*] Sync GRanulator outputs (check dry/wet logic)
// [*] Add tempo slider in Master
// [*] Add Sync slider/button
// [*] Use OSC for handling parameters
// [*] Use OSC for handling master parameters
// [*] Use OSC for handling binary parameters
// [*] Initialize Synths settings from parameters
// [*] Disable Master controls till Audio devices are ready
// [*] Allow grains to be played back in reverse
// [*] Update Slider units based on Sync option
// [*] Convert grain density unit grains/sec to grains/beat
// [*] Convert seconds to milliseconds when Sync is off
// [*] Add modes for Slider range e.g. Mode1: 0.01ms to 1000ms, Mode1: 1/512 note
// [*] Add Tempo Slider for Syncing
// [ ] Add freeze functionality
// [ ] Add feedback functionality
// [ ] Persist Slider parameters
// [ ] Try replacing defer statements and use AppClock for scheduling
// [ ] Ensure there is not delay between the Granulators

)
