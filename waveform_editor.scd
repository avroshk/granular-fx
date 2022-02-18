/*
 * WAVEFORM EDITOR
 * https://sccode.org/1-4VJ
 */
(
s.waitForBoot({

    var size = 1024;
    var canvas, reset, test;
    var wave, waveBuf, testSynth;
    var draw, lastPos, lastVal, update;

    w = Window("Waveform editor", Rect(100, 100, 800, 330));

    wave = Signal.sineFill(size, [1]);
    waveBuf = Buffer.alloc(s, size * 2);
    ~waveBuf = waveBuf;
    testSynth = nil;

    update = { waveBuf.loadCollection(wave.asWavetable); };

    lastPos = nil;
    lastVal = nil;

    draw = { |me, x, y, mod, btn|
        var pos = (size * (x / me.bounds.width)).floor,
            val = (2 * (y / me.bounds.height)) - 1;
        val = min(max(val, -1), 1);
        wave.clipPut(pos, val);
        if(lastPos != nil, {
            for(lastPos + 1, pos - 1, { |i|
                wave.clipPut(i, lastVal + (((i - lastPos) / (pos - lastPos)) * (val - lastVal)));
            });
            for(pos + 1, lastPos - 1, { |i|
                wave.clipPut(i, lastVal + (((i - lastPos) / (pos - lastPos)) * (val - lastVal)));
            });
        });
        lastPos = pos;
        lastVal = val;
        update.value;
    };

    canvas = UserView(w, Rect(0, 30, 800, 300))
        .background_(Color.white)
        .animate_(true)
        .mouseDownAction_(draw)
        .mouseMoveAction_(draw)
        .mouseUpAction_({
            lastPos = nil;
            lastVal = nil;
        })
        .drawFunc_({ |me|
            Pen.moveTo(0@(me.bounds.height * (wave[0] + 1) / 2));
            for(1, size - 1, { |i, a|
                Pen.lineTo((me.bounds.width * i / size)@(me.bounds.height * (wave[i] + 1) / 2))
            });
            Pen.stroke;
        });

    reset = Button(w, Rect(0, 0, 60, 30))
        .states_([["Reset"]])
        .action_({
            wave.sineFill([1]);
            update.value;
        });

    test = Button(w, Rect(60, 0, 60, 30))
        .states_([["Test"], ["Stop"]])
        .action_({ |me|
            if(me.value == 1, {
                update.value;
                testSynth = {
                    var sig;
                    sig = Osc.ar(waveBuf, MouseY.kr(30, 1000)) * 0.1;
                    sig = LeakDC.ar(sig);
                    sig = sig!2;
                }.play;
            }, {
                testSynth.free;
            });
        });

    w.onClose_({
        testSynth.free;
        wave.free;
    });

    w.front;

});

)