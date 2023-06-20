import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
//import java.util.Comparator;

/*
	Leaving this because I thought it was funny in hindsight.
	
 	Here's an idea:
		-map each letter to a prime number
		-iterate through the dictionary and get a collection of all numbers able to be created from
			multiplying the values of each word's letters' values (i.e. tin# == nit#)
		-put each prime to the exponent of however many times that letter made an appearance(?)
		-create a map of all of those numbers to lists containing all strings whose letters create
			that number
	
	If any words can be made from the input, they should be able to divide the input's number
		cleanly,no?

		Thank you been-up-for-nine-hours-working-on-this-crackhead-brain, you were right.
		Went from 10 words from "Todd Neller" every 15 seconds to 13666 in ~1/2 second.
		
		(That'd be one of my professors at Gettysburg; he had a sign beside his door
		displaying an anagram of his name: "Nerd? Do tell!" Pretty much what gave me
		the idea to make this)
		
	--------
	
	Deciding to keep track of each state's possible branches also cut down on the processing time a lot;
	using "Todd Neller" as an example again and a limit of 10,000 anagrams,
	doing that cut down the first run from ~780 ms to ~300, and the subsequent from ~260 ms to ~25 ms.
						woagh
						
	Not having to recompute all of this saves an incredible amount of time for larger strings especially (same limit as above):
	"When the moon hits your eye like a big pizza pie" takes ~5 s on the first run and ~50 ms on subsequent runs, *and*
	typing in "When the moon hits your eye _lkie_ a big pizza pie" after that also takes ~50 ms because it realizes you're using the same letters
	and doesn't have to recompute all of the possible branches from each state.
						woagh (again)
						
						
	--------
	
	Try CPU threading? Just need to figure out what to give each thread, how to decide to create a new one, and how
	to avoid concurrent modification.
*/

public class AnagramGenerator {
	/*
		Strings:
		alpha - just a string containing the alphabet and a space character; defines the characters that
			the user is able to type
		dictFolder - folder in which the lettered subdictionary .txt files will be stored/read from if they exist
		dictFilename - name of the file which the program will read to create subdictionary files;
			I keep this in the main project folder to not have to sift through the "dicts" folder
			that contains the subdictionary files
		outFolder - folder in which the anagram results will be stored
		quickFilename - supplementary file containing the words that can be created from the user's input and
			a value corresponding to the letters contained within them, which themselves are mapped to prime
			numbers so that, say, the value of "tin" == the value of "int"; stored alongside subdictionaries
		input - the user's input, eventually trimmmed of leading and trailing spaces
		output - the name of the file containing the results produced from the user's input; named in the style
			of "~(input).txt"
		
		ints:
		charLim - limit on length of user's input, after being trimmed of leading or trailing spaces
		genLim - limit on number of anagrams to be generated
		numGenned - represents full anagrams generated from the searching process
		
		longs:
		startTime - time at which the main anagram searching begins
		readDictStartTime - time at which the program begins reading from the main dictionary file
			to make the subdictionary files
		timeTaken - represents the time taken to find all (or as many as genLimit allows) anagrams from the input
		
		appear - LinkedList that keeps track of which characters of the alphabet appear in the user's input; used to determine which
		subdictionary files to reference so you don't have to read the whole dictionary every time you want some anagrams;
		all lettered subdictionaries will be used if all of the alphabet's characters are used if you enter something like,
		say, "the quick brown fox jumps over the lazy dog"
		
		lastMult - BigInteger the "mult value" of the non-space characters of the last input received from the user; used
		to quickly compute requests containing the same letters as the last request
		
		dict - the dictionary-reading-and-creating object the search function references; Dictionary is defined below this class
			Dictionary courtesy of dwyl on GitHub (who got it from infochimps)
			https://github.com/dwyl/english-words
	*/
	private static String alpha = "abcdefghijklmnopqrstuvwxyz ", dictFolder = "dicts",
			dictFilename = "words_alpha", outFolder = "results", quickFilename = "quick", input, output;
	private static int charLim = 48, genLim = 100000, numGenned;
	private static long startTime, readDictStartTime, timeTaken;
	private static HashMap<Character, Boolean> appear;
	private static BigInteger lastMult = BigInteger.ZERO;
	private static Dictionary dict;
	
	/*
		Maybe look into storing previous "quicks" somewhere and having the user attempt to retrieve that
		file to speed things up? I.e. this gets faster the more it does.
		Like, maybe store a certain number of previous results and replace the oldest with the newest
		once the limit is hit.
	*/
	
	/**
	* Generates a .txt file containing as many anagrams able to be generated
	* within an optionally specified limit; default is 100000, if unspecified.
	* 
	* @param  args 
	*/
	public static void main(String[] args) {
		Scanner scan = new Scanner(System.in);
		while(scan != null) {
			boolean valid = true;
			System.out.print("Gimme a phrase, or type \"x\" to exit: ");
			
			//Trim input so the system won't be fooled by "         x "-like inputs.
			input = scan.nextLine().trim();
			
			/*
				Quits; you're not gonna get any anagrams from a single-character input,
				so this should be fine to do, I think.
			*/
			if(input.toUpperCase().equals("X")) {
				scan.close();
				System.exit(1);
			}
			//Prompts user for another input if theirs is too short after being trimmed
			else if(input.length() < 2) {
				System.err.println("You've gotta give me *something* to rearrange, man.");
				System.out.println();
				continue;
			}
			else {
				//Trims the user's input in case they type something like "         a hedge maze  ".
				StringBuilder sb = new StringBuilder();
				char last = ' ', c;
				for(int i = 0; i < input.length(); ++i) {
					c = input.charAt(i);
					if(c == ' ') {
						if(last != c) {
							sb.append(c);
						}
						last = c;
					}
					else {
						sb.append(c);
						last = c;
					}
				}
				
				/*
					Just a precautionary thing so you don't melt your computer;
					you can still set it to be really high, though, or disable
					this yourself.
				*/
				if(sb.length() > charLim) {
					System.err.printf("Input must be %s characters or less.\n", charLim);
					System.out.println();
					continue;
				}
				input = sb.toString();
			}
			/*
				appear keeps track of which non-space characters appear in your input;
				used to tell which subdictionaries are needed to be referenced.
			*/
			appear = new HashMap<Character, Boolean>();
			char c;
			//This keeps track of the "mult value" of the input's characters
			BigInteger mult = BigInteger.ONE;
			dict = new Dictionary(dictFolder, dictFilename + ".txt");
			for(int i = 0; i < input.length(); ++i) {
				c = input.charAt(i);
				
				/*
					A check to see if all of the characters are within the alphabet or spaces;
					figured I might as well check here myself instead of doing an extra loop
					or something above with the other error message checks.
				*/
				if(alpha.indexOf(c) == -1) {
					valid = false;
					break;
				}
				else if(c != ' ') {
					appear.put(c, true);
					mult = mult.multiply(BigInteger.valueOf(dict.getPrime((c))));
				}
			}
			
			//Only proceed if user input is not malformed
			if(valid) {
				//Makes directory for subdictionaries if it doesn't exist
				File dir = new File(dictFolder);
				if(!dir.exists()) {
					dir.mkdir();
				}
				boolean needToRead = false;
				//Check to see if the dictionary whose name is specified with dictFilename exists
				File newDictCheck = new File('~' + dictFilename);
				if(newDictCheck.exists()) {
					File charFile;
					for(int i = 0; i < alpha.length() - 1; ++i) {
						//If any of the lettered subdictionaries don't exist, read them in
						charFile = new File(dict.getTxtFile(alpha.charAt(i)));
						if(!charFile.exists()) {
							needToRead = true;
							break;
						}
					}
				}
				//Only read dictionary anew if either of these are tripped
				if(needToRead || !newDictCheck.exists()) {
					try {
						readDictStartTime = System.currentTimeMillis();
						
						dict.readDict();
						newDictCheck.createNewFile();
						
						System.out.printf("Finished sorting and making dictionary files in %d ms.\n",
								System.currentTimeMillis() - readDictStartTime);
					}
					catch (IOException e) {
						System.err.println("Could not create temporary dictionary file.");
					}
				}
				startTime = System.currentTimeMillis();

				FileWriter quickWriter = null;
				//This run is either the first run or the last run's character count was different
				if(!mult.equals(lastMult)) {
					try {
						lastMult = mult;
						//Get all of the words able to be made from the user's input
						dict.quicken(appear, input.length(), mult);
						quickWriter = new FileWriter(dict.getTxtFile(quickFilename));
					}
					catch (IOException e) {
						System.err.println("IOE in !mult.equals(lastMult)");
					}
				}
				
				//Reset the number of generated anagrams for a clean run
				numGenned = 0;
				output = outFolder + "\\~" + input + ".txt";
				
				//Makes the folder for the results if it doesn't exist
				dir = new File(outFolder);
				if(!dir.exists()) {
					dir.mkdir();
				}
				try {
					//resultWriter writes to the result file. Duh.
					FileWriter resultWriter = new FileWriter(output);
					resultWriter.write("\"" + input + "\" anagrams:\n\n");
					
					/*
						A blank state serving as the root of the search. The zero is a holdover explained
						within the State class that I'm leaving in case whoever's gotten their hands on this
						code wants to play with it. Ignore it otherwise.
					*/
					
					State root = new State("", mult/*, 0*/);
					
					//This is where the magic happens, baby
					search(resultWriter, quickWriter, root);
					
					timeTaken = System.currentTimeMillis() - startTime;
					
					resultWriter.write("\n\n" + numGenned + " anagrams generated in " + (timeTaken + " ms"));
					resultWriter.close();
					
					/*
						Should only be null if you entered something with the same letters and same number of
						each letters as the last thing you entered.
					*/
					if(quickWriter != null) {
						quickWriter.close();
					}
					System.out.printf("%d anagrams generated from \"%s\" in %d ms; written to %s\n", numGenned, input, timeTaken, output);
					//Reactivate this if you want a log file of this run
//					FileWriter logWriter = new FileWriter("results\\~" + lastMult + ".log", true);
//					logWriter.write(Long.toString(timeTaken) +'\n');
//					logWriter.close();
				}
				catch(IOException e) {
					System.err.println("Could not create file to write to.");
				}
			}
			else {
				System.err.println("Input must consist exclusively of English alphabet characters, with or without spaces.");
			}
			System.out.println();
		}
	}
	
	//Uses an ArrayDeque to perform DFS (by default) to find anagrams.
	private static void search(FileWriter resultWriter, FileWriter quickWriter, State root) {
		/*
			Main queue holding the states to be processed. Apparently faster as an ArrayDeque than
			a LinkedList or Stack when used for DFS/BFS, respectively; I think it's right, from testing.
		*/
		//
		ArrayDeque<State> queue = new ArrayDeque<State>();
		
		//HashMap mapping multValues to strings; "int" mV == "nit" mV, and are logged under "int" mV.
		HashMap<BigInteger, ArrayDeque<String>> mult2Strs = dict.getMult2Strs();
		
		/*
			HashMap keeping track of which words are able to be pulled from each intermediate state;
			helps greatly with processing speed.
		*/
		HashMap<BigInteger, ArrayDeque<BigInteger>> usableFromState = dict.getUsableFromState();
		
		//Local list of mV's corresponding to Strings able to be used from the current state.
		ArrayDeque<BigInteger> usable;

		/*
			Variables used to store BigDecimal conversions of each current state's mV and the mV of
			the next word being checked to see if it can be used; used in some math shenanigans.
		*/
		BigDecimal cMVDec, keyDec;
		
		/*
			StringBuilders keeping the results to be written to the result and quick files, respectively.
			sb writes fully completed anagrams, while quick writes the results stored in usable.
		*/
		StringBuilder sb, qb;
		
		State curr;
		queue.push(root);
		try {
			while(!queue.isEmpty()) {
				curr = queue.pop();
					/*
						See if this state's mV has been encountered; if so, you know how many of
						which letters are left for use; otherwise, process and log
						
						Most of this is copied-and-pasted because, to my understanding,
						sectioning each half off into different methods would require more overhead,
						so I opted not to do that for memory and speed
					*/
					usable = usableFromState.get(curr.multValue);
					if(usable == null) {
						//Reinitialize the builder for quick's file
						qb = new StringBuilder(curr.multValue.toString());
						qb.append(' ');
						
						//Reinitialize usable to keep track of this state's stuff
						usable = new ArrayDeque<BigInteger>();
						
						/*
							Iterate through all words able to be made from the input and check to see if
							they can be used from the current state
						*/
						for(BigInteger key : mult2Strs.keySet()) {
							/*
								Checks if the next word completes the anagram or can extend it
								(0 or 1, respectively; -1 would indicate that the next word doesn't
								uses a letter that the current state doesn't have available or is
								too long or something)
							*/
							switch(curr.multValue.compareTo(key)) {
								//Next word completes the anagram
								case 0:
									//Add this key to this state's mV's "usable" list
									usable.add(key);
									
									qb.append(key);
									qb.append(' ');
									//Check to see if the whole input alrady has an anagram in the dictionary
									if(curr.multValue.equals(lastMult)) {
										//Whole-word anagram exists; write all from m2S into results
										for(String s : mult2Strs.get(key)) {
											sb = new StringBuilder(s);
											//Getting back what you put in wouldn't be fun, right?
											if(!input.equals(sb.toString())) {
												sb.append('\n');
												resultWriter.write(sb.toString());
												++numGenned;
												//Check anagram generation limit; break if so
												if(numGenned == genLim) {
													queue = new ArrayDeque<State>();
													break;
												}
											}
										}
									}
									else {
										//Write each completed anagram to the result file
										for(String s : mult2Strs.get(key)) {
											sb = new StringBuilder(curr.curr);
											sb.append(' ');
											sb.append(s);
											sb.append('\n');
											resultWriter.write(sb.toString());
											resultWriter.flush();
											++numGenned;
											//Check anagram generation limit; break if so
											if(numGenned == genLim) {
												queue = new ArrayDeque<State>();
												break;
											}
										}
									}
									//Always remember to break your switch cases, kids (source of bugs)
									break;
								//Next word might extend the anagram-to-be
								case 1:
									//Convert and store the current and key mV's to BigDecimals for math
									cMVDec = new BigDecimal(curr.multValue);
									keyDec = new BigDecimal(key);
									
									/*
										remainder.equals(zero) means the next word uses at least some of the letters
										the current state has remaining because each state's mV is derived
										from the multiplication of prime numbers
									*/
									if(cMVDec.remainder(keyDec).equals(BigDecimal.ZERO)) {
										//Add key to state's usable list
										usable.add(key);
										
										qb.append(key);
										qb.append(' ');
										if(curr.curr.length() > 0) {
											//State is already in-progress
											for(String s : mult2Strs.get(key)) {
												sb = new StringBuilder(curr.curr);
												sb.append(' ');
												sb.append(s);
												
												/*
													Send new state to queue with the new word and a space, and update new state
													to have current state's mV divided by the current word's.
													
													The curr.spaces thing is a leftover from when I tried out a PriorityQueue
													instead of a normal Stack or Queue. Check out the State class for more stuff,
													and ignore it otherwise.
													
												*/
												queue.add(new State(sb.toString(), cMVDec.divide(keyDec, RoundingMode.HALF_EVEN).toBigInteger()/*, curr.spaces + 1*/));
											}
										}
										//Current state is root (empty)
										else {
											for(String s : mult2Strs.get(key)) {
												//Send new state to queue; starts a sentence
												queue.add(new State(s, cMVDec.divide(keyDec, RoundingMode.HALF_EVEN).toBigInteger()/*, curr.spaces*/));
											}
										}
									}
									//That said, I don't think I need to do one here since it's the last
//									break;
							}
							if(numGenned == genLim) {
								queue = new ArrayDeque<State>();
								System.out.printf("Entry limit of %d exceeded\n", genLim);
								break;
							}
						}
						/*
							Store the current mV's usable words for potential later use, even if there's
							nothing added to the list; it'll just mean that you'll cut out unnecessary
							loops later, if another state with the same mV rolls around
						 */
						usableFromState.put(curr.multValue, usable);
						//Write to the "quick" file for another run with the same letters
						qb.append('\n');
						quickWriter.write(qb.toString());
						quickWriter.flush();
						qb.setLength(0);
					}
					
					
					/*
						This else does the above in the same way, but uses what usable has stored.
						
						If this run's input contains the same characters and number of characters
						as the last, only this else will be executed, because this program stores
						the usable list of the last and reuses it if so.
						
						Otherwise, this else will only be entered if another state with the same mV
						as the current's has been processed because then you'd be able to just loop
						through the "usables" kept track by usable much more quickly than all of the
						possible subwords kept track by mult2Strs; you've already done most of the
						work for this state.
						
						Shouldn't have to recomment stuff because of that; ignore it all, really.
					*/
					else {
						for(BigInteger key : usableFromState.get(curr.multValue)) {
							switch(curr.multValue.compareTo(key)) {
								case 0: 
									if(curr.multValue.equals(lastMult)) {
										//Whole-word anagram exists; write all from m2S into results
										for(String s : mult2Strs.get(key)) {
											sb = new StringBuilder(s);
											//Getting back what you put in wouldn't be fun, right?
											if(!input.equals(sb.toString())) {
												sb.append('\n');
												resultWriter.write(sb.toString());
												++numGenned;
												//Check anagram generation limit; break if so
												if(numGenned == genLim) {
													queue = new ArrayDeque<State>();
													break;
												}
											}
										}
									}
									else {
										for(String s : mult2Strs.get(key)) {
											sb = new StringBuilder(curr.curr);
											sb.append(' ');
											sb.append(s);
											sb.append('\n');
											resultWriter.write(sb.toString());
											resultWriter.flush();
											++numGenned;
											if(numGenned == genLim) {
												queue = new ArrayDeque<State>();
												break;
											}
										}
									}
									break;
								case 1:
									cMVDec = new BigDecimal(curr.multValue);
									keyDec = new BigDecimal(key);
									if(curr.curr.length() > 0) {
										for(String s : mult2Strs.get(key)) {
											sb = new StringBuilder(curr.curr);
											sb.append(' ');
											sb.append(s);
											queue.add(new State(sb.toString(), cMVDec.divide(keyDec, RoundingMode.HALF_EVEN).toBigInteger()/*, curr.spaces + 1*/));
										}
									}
									else {
										for(String s : mult2Strs.get(key)) {
											queue.add(new State(s, cMVDec.divide(keyDec, RoundingMode.HALF_EVEN).toBigInteger()/*, curr.spaces*/));
										}
									}
//									break;
							}
							if(numGenned == genLim) {
								queue = new ArrayDeque<State>();
								System.out.printf("Entry limit of %d exceeded\n", genLim);
								break;
							}
						}
					}
			}
		}
		catch(IOException e) {
			System.err.println("IOE");
			}
		}
	
	public static String toString(List<Character> list) {
		StringBuilder sb = new StringBuilder();
		for(char c : list) {
			sb.append(c);
		}
		return sb.toString();
	}
}

class Dictionary {
	/*
		Dictionary courtesy of dwyl on GitHub (who got it from infochimps)
			https://github.com/dwyl/english-words
			
		Strings:
		alpha - the characters of the English alphabet, in a String
		dictFilename - the name of the dictionary file that dictRead() accesses
		folder - folder in which the subdictionary files will be saved to
		
		buildLim - limit on how many words will be read before the writer writes
			to the current subdictionary file
		
		primes - the first twenty-six positive prime numbers; will be mapped to
			the letters of the alphabet
		
		last - last character file loaded; starts with 'a' to work with the loop
			I bodged (thanks for the word, Tom)
		
		dict - stores the words of the dictionary file
		
		dictExceptiosn - contains the only one- and two-letter words I thought
		were relevant enough to use; like, the shortest words or exclamations or
		whatever I thought people commonly use
		
		chr2Prime - the map of letters to the prime numbers from primes
		
		mult2Strs - maps multValues to Strings
		
		usableFromState - keeps track of which Strings' multValues can be used to continue the current state		
	*/
	private String alpha = "abcdefghijklmnopqrstuvwxyz", dictFilename, folder;
	private int buildLim = 10000;
	private int[] primes = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97, 101};
	private String[] exceptionList = {"a", "i", "o", "u", "ad", "ah", "ai", "am", "an", "as", "at", "be", "by", "do", "ed", "eh",
			"em", "er", "go", "ha", "he", "hi", "hm", "ho", "id", "if", "in", "is", "it", "jo", "la", "lo", "ma", "me", "mi",
			"mm", "mo", "mu", "my", "na", "no", "of", "oh", "oi", "om", "on", "op", "or", "ow", "ox", "oy", "pa", "so", "ta",
			"to", "uh", "um", "up", "us", "we", "ya", "ye", "yo"};
	private char last = 'a';
	private LinkedList<String> dict;
	private HashMap<String, Boolean> dictExceptions;
	private static HashMap<Character, Integer> chr2Prime;
	private static HashMap<BigInteger, ArrayDeque<String>> mult2Strs;
	private static HashMap<BigInteger, ArrayDeque<BigInteger>> usableFromState = new HashMap<BigInteger, ArrayDeque<BigInteger>>();
	
	public Dictionary(String folder, String dictFilename) {
		this.folder = folder;
		this.dictFilename = dictFilename;
		makeExceptions();
		buildPrimeMap();
	}
	
	/*
		Reads in the given dictionary file; works under the assumption that it's just a file
		with a singular word on each line, and nothing else
	*/
	
	public void readDict() {
		try {
			FileWriter writer = new FileWriter(getTxtFile(last));
			try {
				System.out.println("Getting dict file " + dictFilename);
				Scanner scan = new Scanner(new File(dictFilename));
				dict = new LinkedList<String>();
				String line;
				while(scan.hasNext()) {
					line = scan.nextLine();
					/*
						!dict.contains(line) makes this run reeeeeeeeeeeeeeeeeeeeeeeeeeeally slow, or at least it did,
						when this was still a LinkedList. ArrayList didn't fare better. Not totally necessary, anyway.
						Just did this in case the dictionary had duplicates.
					*/
					if((line.length() > 2 || dictExceptions.get(line) != null) /* && !dict.contains(line)*/) {
						dict.add(line);
					}
				}
				
				/*
					Just in case; the file I'm working with considers j and y to be
					the same character and is sorted (wrongly; I'm from Burgerland)
					in that way, so this fixes that
				*/
				Collections.sort(dict);
				
				//Read in the dictinary's words and store them in subdictionary files
				StringBuilder sb = new StringBuilder();
				for(String s : dict) {
					//Create a new subdictionary file if the current word's next letter differs
					if(last != s.charAt(0)) {
						writer.write(sb.toString());
						writer.flush();
						sb.setLength(0);
						last = s.charAt(0);
						writer = new FileWriter(new File(getTxtFile(last)));
					}
					sb.append(s);
					sb.append('\n');
					if(sb.length() > buildLim) {
						writer.write(sb.toString());
						writer.flush();
						sb.setLength(0);
					}
				}
				writer.write(sb.toString());
				
				writer.close();
				scan.close();
			}
			catch (FileNotFoundException e) {
				System.err.println("FNFE in readDict()");
			}
		}
		catch (IOException e) {
			System.err.println("IOE");
		}
	}
	
	protected void quicken(HashMap<Character, Boolean> appear, int inputLength, BigInteger inputMultVal) {
		//Some jank because 
		//
		
		/*
			scan - a Scanner used to read the subdictionary files; if scan isn't initialized somewhere outside of the
			upcoming for() loop, the scan.close() at the end of this method complains about it, so there's this jank
			
			word - the current word of the subdictionary
			
			lastMultDec - BigDecimal conversion of the input's multValue
			
			mult - current word's "multValue"
			
			anagrams - list of words that correspond to a particular multValue;
				retrieved from mult2Strs
		*/
		Scanner scan = new Scanner("");
		String word;
		BigDecimal inputMultDec = new BigDecimal(inputMultVal);
		BigInteger mult;
		ArrayDeque<String> anagrams;

		mult2Strs = new HashMap<BigInteger, ArrayDeque<String>>();
		
		try {
			//Get all letters that appear in the user's input
			for(char key : appear.keySet()) {
				scan = new Scanner(new File(getTxtFile(key)));
				while(scan.hasNext()) {
					word = scan.nextLine();
					//This'll probably make it run faster in the long run, so I'll keep it
					if(word.length() <= inputLength) {
						mult = BigInteger.valueOf(getMultValue(word));
						/*
							remainder.equals(zero) means this word uses only letters found in and has no more
							specific letter usages than the input
						*/
						if(inputMultDec.remainder(new BigDecimal(mult)) == BigDecimal.ZERO) {
							anagrams = mult2Strs.get(mult);
							//Start keeping track of the anagrams of this word if none have been found
							if(anagrams == null) {
								anagrams = new ArrayDeque<String>();
							}
							anagrams.add(word);
							mult2Strs.put(mult,  anagrams);
						}
					}
				}
			}
		}
		catch(FileNotFoundException e) {
			System.err.println("FNFE in quicken()");
		}
		scan.close();
	}

	/*
		This quicken() is called when your second input contains all the same letters as the last one
		you did, and it reads from the file dictated from "quick" up above in AnagramGenerator.
	*/
	public void quicken(String quick) {
		Scanner scan;
		try {
			scan = new Scanner(new File(getTxtFile(quick)));
			String line;
			String[] splits;
			BigInteger mult;
			ArrayDeque<BigInteger> list;
			
			usableFromState = new HashMap<BigInteger, ArrayDeque<BigInteger>>();
			
			while(scan.hasNext()) {
				line = scan.nextLine();
				splits = line.split(" ");
				list = new ArrayDeque<BigInteger>();
				mult = BigInteger.valueOf(Long.parseLong(splits[0]));
				for(int i = 1; i < splits.length; ++i) {
					list.add(new BigInteger(line));
				}
				usableFromState.put(mult, list);
			}
		}
		catch (FileNotFoundException e) {
			System.err.println("FNFE");
		}
	}
	
	//Maps the letters of the alphabet to the list of prime numbers above
	private void buildPrimeMap() {
		chr2Prime = new HashMap<Character, Integer>();
		for(int i = 0; i < primes.length; ++i) {
			chr2Prime.put(alpha.charAt(i), primes[i]);
		}
	}
	
	//Calculates and returns the "multValue" of the given String
	private long getMultValue(String s) {
		long mult = 1;
		for(int i = 0; i < s.length(); ++i) {
			mult *= chr2Prime.get(s.charAt(i));
		}
		return mult;
	}
	
	//Creates a the map of dictionary exceptions
	private void makeExceptions() {
		dictExceptions = new HashMap<String, Boolean>();
		for(String s : exceptionList) {
			dictExceptions.put(s, true);
		}
	}
	
	
	/*
		These are a bunch of helper methods used sparingly; I hope they're
		self-explanatory enough to not need explaining.
	*/
	
	public String getTxtFile(char c) {
		StringBuilder sb = new StringBuilder();
		sb.append(folder);
		sb.append('\\');
		sb.append(c);
		sb.append(".txt");
		return sb.toString();
	}
	
	public String getTxtFile(String s) {
		StringBuilder sb = new StringBuilder();
		sb.append(folder);
		sb.append('\\');
		sb.append(s);
		sb.append(".txt");
		return sb.toString();
	}
	
	public int getPrime(char c) {
		return chr2Prime.get(c);
	}
	
	public HashMap<BigInteger, ArrayDeque<String>> getMult2Strs() {
		return mult2Strs;
	}
	
	public HashMap<BigInteger, ArrayDeque<BigInteger>> getUsableFromState() {
		return usableFromState;
	}
}

class State {
	/*
		curr - represents the current state of the... state.
			*cough*
			It's the anagram-in-progress of the state.
		
		multValue - a value corresponding to (n * p)_a * (n * p)_b ... * (n * p)_z, where:
		n = number of appearances of a particular letter,
		p = the prime number mapped to the particular letter, as dictated within
			the Dictionary class
			
			This is used so that a word can be tested to see if it uses any of and only
			the letters a state has left with a single division - if the remainder of the
			operation is zero, because the value is made of primes, it uses acceptable letters
			and no others.
				...This is probably better explained in the Dictionary class.
	*/
	public String curr;
	public BigInteger multValue;

/*
	Tried using a PriorityQueue to get the results in order of number of spaces used and
	then alphabetically within that tier, but that was really slow. Left here in case I
	change my mind or whoever uses this wants to try it; make the queue into a PQ,
	un-dummy the stuff concerning the number of spaces the state uses within the class
	and the search() method use the StateComparator below if you do.
*/
//	public int spaces;
	
	public State(String curr, BigInteger multValue/*, int spaces*/) {
		this.curr = curr;
		this.multValue = multValue;
//		this.spaces = spaces;
	}
}

//See the above State class.

//class StateComparator implements Comparator<State> {
//	public int compare(State s1, State s2) {
//		if(s1.spaces == s2.spaces) {
//			return 0;
//		}
//		else if(s1.spaces == s2.spaces) {
//			return -1;
//		}
//		return 1;
//	}
//	
//}