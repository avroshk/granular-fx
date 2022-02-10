(
var w;
var s1, s2, s3, s4, s5;
var d1, d2, d3, d4, d5;
var g1, g2, g3, g4, g5;
var n1, n2, n3, n4, n5;
var k1;
var b1;
var a1;
var left=20, top=60, width=200, height=20, margin=30;

w = Window("GranularFX", Rect(600,200,500,300));
w.front;


s1 = Slider(w, Rect(left, top+(margin*0), width, height));
d1 = StaticText.new(w, Rect(width+margin, top+(margin*0), width+margin, height));
g1 = ControlSpec(1,40,step:1,default:20,units:"Hz");
n1 = "Trigger rate";
d1.string = n1;

s2 = Slider(w, Rect(left, top+(margin*1), width, height));
d2 = StaticText.new(w, Rect(width+margin, top+(margin*1), width+margin, height));
g2 = ControlSpec(0,2,default:0.5,units:"s");
n2 = "Grain size";
d2.string = n2;

s3 = Slider(w, Rect(left, top+(margin*2), width, height));
d3 = StaticText.new(w, Rect(width+margin, top+(margin*2), width+margin, height));
g3 = ControlSpec(-2,2,default:1,units:"ratio");
n3 = "Grain playback rate";
d3.string = n3;

s4 = Slider(w, Rect(left, top+(margin*3), width, height));
d4 = StaticText.new(w, Rect(width+margin, top+(margin*3), width+margin, height));
g4 = ControlSpec(0,50,1,default:20,units:"Hz");
n4 = "Start Pos Shimmer Rate";
d4.string = n4;

s5 = Slider(w, Rect(left, top+(margin*4), width, height));
d5 = StaticText.new(w, Rect(width+margin, top+(margin*4), width+margin, height));
g5 = ControlSpec(0,2,default:0.1,units:"rate");
n5 = "Start Pos Ramp";
d5.string = n5;

b1 = NumberBox(w, Rect(150, 10, 100, 20));
k1 = Knob.new(w, Rect(left, 10, 40, 40))
.centered_(true)
.mode_(\horiz);
k1.value = 0.1;
k1.action = {
	arg v;
	b1.value_(v.value);
};


a1 = Button(w, Rect(130, 130, 100, 100));
a1.states = ["sync-on", "sync-off"];
s1.action = {
	var val = g1.map(s1.value);
	~g.set(\dens, val);
	d1.string = n1
	+ val.round(0.1)
	+ "Hz"
};
s2.action = {
	var val = g2.map(s2.value);
	c.set(\dur, val);
	d2.string = n2
	+ val.round(0.01)
	+ "sec"
};
s3.action = {
	var val = g3.map(s3.value);
	c.set(\rate, val);
	d3.string = n3
	+ val.round(0.1)
	+ "ratio"
};
s4.action = {
	var val = g4.map(s4.value);
	c.set(\grainStartPosShimmerRate, val);
	d4.string = n4
	+ val.round(0.1)
	+ "Hz"
};
s5.action = {
	var val = g5.map(s5.value);
	c.set(\grainStartPosRamp, val);
	d5.string = n5
	+ val.round(0.1)
};
)






