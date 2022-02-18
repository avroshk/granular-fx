(
var mixSection, timeSection, pitchSection, panSection, xyPad1Title, xyPad2Title;
var mixControl, gainControl, grainDensityControl, grainSizeControl, pitchControl,
panControl, delayControl, tempoControl, xyPad1Control, xyPad2Control;
var grainSizeModControl, pitchModControl, panModControl, delayModControl;
var triggerTypeMenu, xMappingMenu, yMappingMenu;
var w, id, xyWindow1, xyWindow2;

var top, left, width, height, margin, xyPadSize, unitWidth,
sliderWidth, sliderHeight;

top = 400;
left = 20;
width = 520;
height = 510;
margin = 20;
xyPadSize = 200;
unitWidth = 70;
sliderWidth = 500;
sliderHeight = 24;
~xMap = grainSizeControl;
~yMap = delayControl;


id = s.nextNodeID; // generate a note id.

// make the window
w = Window("GranularFX", Rect(left, top, width, height));
w.front; // make window visible and front window.
w.view.decorator = FlowLayout(w.view.bounds);

/*w.view.background = Color.gray; */


// create controls for all parameters
w.view.decorator.nextLine;

(
mixSection = StaticText(w, Rect(10, 10, width, 20));
mixSection.string = "Mix";
mixSection.align = \center;
mixSection.font = Font("Monaco", 16);
);

mixControl = EZSlider(
	parent:w,
	bounds:sliderWidth @ sliderHeight,
	label:"Dry / Wet ",
	controlSpec:ControlSpec(0, 1, \amp, 0.01),
	action: {
		arg x;
		~g.set(\mix, (1-x.value));
		// (1-x.value).postln;
	},
	unitWidth:unitWidth,
	initVal:0.0,
	labelWidth:120,
	numberWidth:50
);


gainControl = EZSlider(
	parent:w,
	bounds:sliderWidth @ sliderHeight,
	label:"Gain ",
	controlSpec:ControlSpec(0, 1, \amp, 0.01),
	action: {
		arg x;
		~g.set(\amp, x.value);
		// x.value.postln;
	},
	unitWidth:unitWidth,
	initVal:1.0,
	labelWidth:120,
	numberWidth:50
);

(
mixSection = StaticText(w, Rect(10, 10, width, 20));
mixSection.string = "Time";
mixSection.align = \center;
mixSection.font = Font("Monaco", 16);
);

grainDensityControl = EZSlider(
	parent:w,
	bounds:sliderWidth @ sliderHeight,
	label:"Grain Density ",
	controlSpec:ControlSpec(1, 256, \lin, 1, units: " grains/sec"),
	action: {
		arg x;
		~g.set(\dens, x.value);
		// x.value.postln;
	},
	unitWidth:unitWidth,
	initVal:16.0,
	labelWidth:120,
	numberWidth:50
);

grainSizeControl = EZSlider(
	parent:w,
	bounds:sliderWidth @ sliderHeight,
	label:"Grain Size ",
	controlSpec:ControlSpec(0.001, 3, \lin, 0.001, units: " sec"),
	action: {
		arg x;
		~g.set(\baseDur, x.value);
		// x.value.postln;
	},
	unitWidth:unitWidth,
	initVal:0.05,
	labelWidth:120,
	numberWidth:50
);

grainSizeModControl = EZSlider(
	parent:w,
	bounds:sliderWidth @ sliderHeight,
	label:"Grain Size Mod",
	controlSpec:ControlSpec(0, 200, \lin, 1, units: " %"),
	action: {
		arg x;
		~g.set(\durRand, (1+(x.value.linlin(0,200,0,2))));
		// (1+(x.value.linlin(0,200,0,2))).postln;
	},
	unitWidth:unitWidth,
	initVal:0,
	labelWidth:120,
	numberWidth:50
);

delayControl = EZSlider(
	parent:w,
	bounds:sliderWidth @ sliderHeight,
	label:"Grain Delay ",
	controlSpec:ControlSpec(0.001, 3, \lin, 0.001, units: " sec"),
	action: {
		arg x;
		~g.set(\ptrSampleDelay, s.sampleRate*x.value);
		// (s.sampleRate*x.value).postln;
	},
	unitWidth:unitWidth,
	initVal:0.0,
	labelWidth:120,
	numberWidth:50
);

delayModControl = EZSlider(
	parent:w,
	bounds:sliderWidth @ sliderHeight,
	label:"Grain Delay Mod",
	controlSpec:ControlSpec(0.001, 3, \lin, 0.001, units: " sec"),
	action: {
		arg x;
		~g.set(\ptrRandomSamples, (s.sampleRate*x.value))
		// (s.sampleRate*x.value).postln;
	},
	unitWidth:unitWidth,
	initVal:0,
	labelWidth:120,
	numberWidth:50
);

triggerTypeMenu = EZPopUpMenu.new(
	parentView:w,
	bounds:292 @ 30,
	label:"Grain Excitation Func",
	labelHeight:30,
	labelWidth:120
);
triggerTypeMenu.addItem(name:\dust, action:{ ~g.set(\sync, 0); });
triggerTypeMenu.addItem(name:\impulse, action:{ ~g.set(\sync, 1); });
triggerTypeMenu.addItem(name:\synced_dust, action:{ ~g.set(\sync, 2); });
triggerTypeMenu.value=1;

tempoControl = EZSlider(
	parent:w,
	bounds:sliderWidth @ sliderHeight,
	label:"Sync Tempo ",
	controlSpec:ControlSpec(20, 999, \lin, 1, units: " bpm"),
	action: {
		arg x;
		~g.set(\tempo, x.value);
		// (s.sampleRate*x.value).postln;
	},
	unitWidth:unitWidth,
	initVal:170,
	labelWidth:120,
	numberWidth:50
);

(
mixSection = StaticText(w, Rect(10, 10, width, 20));
mixSection.string = "Pitch";
mixSection.align = \center;
mixSection.font = Font("Monaco", 16);
);

pitchControl = EZSlider(
	parent:w,
	bounds:sliderWidth @ sliderHeight,
	label:"Pitch ",
	controlSpec:ControlSpec(-3600, 3600, \lin, 10, units: " cents"),
	action: {
		arg x;
		~g.set(\rate, (x.value/100).midiratio);
		// (x.value/100).midiratio.postln;
	},
	unitWidth:unitWidth,
	initVal:0,
	labelWidth:120,
	numberWidth:50
);

pitchModControl = EZSlider(
	parent:w,
	bounds:sliderWidth @ sliderHeight,
	label:"Pitch Mod",
	controlSpec:ControlSpec(0, 3600, \lin, 10, units: " cents"),
	action: {
		arg x;
		~g.set(\rateRand, (x.value/100).midiratio);
		// (x.value/100).midiratio.postln;
	},
	unitWidth:unitWidth,
	initVal:0,
	labelWidth:120,
	numberWidth:50
);

(
mixSection = StaticText(w, Rect(10, 10, width, 20));
mixSection.string = "Stereo Image";
mixSection.align = \center;
mixSection.font = Font("Monaco", 16);
);

panControl = EZSlider(
	parent:w,
	bounds:sliderWidth @ sliderHeight,
	label:"Pan ",
	controlSpec:ControlSpec(-1, 1, \lin, 0.1),
	action: {
		arg x;
		~g.set(\pan, x.value);
		// x.value.postln;
	},
	unitWidth:unitWidth,
	initVal:0,
	labelWidth:120,
	numberWidth:50
);

panModControl = EZSlider(
	parent:w,
	bounds:sliderWidth @ sliderHeight,
	label:"Pan Mod",
	controlSpec:ControlSpec(0, 100, \lin, 1, units: " %"),
	action: {
		arg x;
		~g.set(\panRand, x.value.linlin(0,100,0,1));
		// (x.value/100).postln;
	},
	unitWidth:unitWidth,
	initVal:0,
	labelWidth:120,
	numberWidth:50
);

/*
(
mixSection = StaticText(w, Rect(10, 10, width, 20));
mixSection.string = "XY Pad";
mixSection.align = \center;
mixSection.font = Font("Monaco", 16);
);

xMappingMenu = EZPopUpMenu.new(
	parentView:w,
	bounds:292 @ 30,
	label:"X Map",
	labelHeight:30,
	labelWidth:120
);
xMappingMenu.addItem(name:\dust, action:{ ~g.set(\sync, 0); });
xMappingMenu.addItem(name:\impulse, action:{ ~g.set(\sync, 1); });
xMappingMenu.addItem(name:\synced_dust, action:{ ~g.set(\sync, 2); });
xMappingMenu.value=1;*/



xyWindow1 = Window("XY Pad", Rect(width+left, top+(height-xyPadSize), xyPadSize+margin, xyPadSize+(margin*2)));
xyWindow1.front;

xyPad1Control = Slider2D(xyWindow1, Rect(10, 10+margin, xyPadSize, xyPadSize));
xyPad1Control.action = {
	arg sl;
	grainSizeControl.valueAction = sl.x.linlin(0,1, 0.001, 3);
	delayControl.valueAction = sl.y.linlin(0,1, 0.001, 3);
};

xyPad1Title = StaticText(xyWindow1, Rect(5, 5, xyPadSize, margin));
xyPad1Title.string = "Grain Size x Grain Delay";
xyPad1Title.align = \center;
xyPad1Title.font = Font("Monaco", 12);

//
xyWindow2 = Window("XY Pad", Rect(width+left, top-60, xyPadSize+margin, xyPadSize+(margin*2)));
xyWindow2.front;

xyPad2Control = Slider2D(xyWindow2, Rect(10, 10+margin, xyPadSize, xyPadSize));
xyPad2Control.action = {
	arg sl;
	grainDensityControl.valueAction = sl.x.linlin(0,1, 1, 256);
	grainSizeControl.valueAction = sl.y.linlin(0,1, 0.001, 3);
};

xyPad2Title = StaticText(xyWindow2, Rect(5, 5, xyPadSize, margin));
xyPad2Title.string = "Grain Density x Grain Size";
xyPad2Title.align = \center;
xyPad2Title.font = Font("Monaco", 12);


// Set default values
gainControl.valueAction = 1;
mixControl.valueAction = 0;
grainDensityControl.valueAction = 16;
grainSizeControl.valueAction = 0.05;
grainSizeModControl.valueAction = 0;
pitchControl.valueAction = 0.0;
panControl.valueAction = 0.0;
delayControl.valueAction = 0.3;
delayModControl = 0;

w.onClose = {
	~g.free;
	xyWindow1.close;
	xyWindow2.close;
	// CmdPeriod.remove(cmdPeriodFunc);
};
)

// [*] 0. Connect Elements
// [*] 1. Tempo Sync
// [*] 2. 2d Slider
// [ ] 3. Trigger Editor
// [ ] 4. Build visualizations to convey ideas
// [ ] 5. Test audio on Zoom
// [ ] 6. Simplify scripts for 1-click Run
// [ ]     6a. Allow easy setup of Input device
// [ ] 7. Document code
// [ ] 8. Record example
