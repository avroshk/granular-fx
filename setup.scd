(
// 1. Add custom granularFX Classes to local extensions directory

var classDefFile = thisProcess.nowExecutingPath.dirname +/+ "granularFXClasses.sc";
var target = Platform.userExtensionDir +/+ "local" +/+ "granularFXClasses.sc";

if (File.exists(target), { File.delete(target);}, { "Local extensions directory is clean.".postln; });
if (File.exists(classDefFile)) { File.copy(classDefFile, target); }


// 2. Recompile Class Library for the changes to take effect
/*
When your class library changes, either because you have installed or removed a Quark, or just modified a class file, you will need to recompile the class library in order for changes to take effect. You can do that quickly via the Ctrl+Shift+L shortcut, or via the Language menu -> Recompile Class Library.
 */

)