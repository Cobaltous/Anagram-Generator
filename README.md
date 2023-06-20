# Anagram-Generator
A small program I made fresh out of college so I'd have something to display here.

Run it with the standard javac and java commands to get it running, provide it with as many words or phrases as you want, and then type "x" to exit. It only accepts characters found in the English alphabet and spaces. Results should get spat out into files found in a /results folder it'll create.

Optionally, also provide it with:

"--dictname (filename)" to use your own dictionary file,

"--charLimit (limit)" to set your own character limit to keep your requests moderate, and/or

"--genLimit (limit)" to cap the number of anagrams written to the results file.

All of these should fall back to their defaults should something get entered wrong, and it'll also tell you if that happens.

Only begins to run poorly when you give it something using too many unqiue letters of the alphabet, which I'm trying to think of how to fix.
This is also mentioned in the code file itself, but just to be extra clear:

			Dictionary courtesy of dwyl on GitHub (who got it from infochimps)
			https://github.com/dwyl/english-words
