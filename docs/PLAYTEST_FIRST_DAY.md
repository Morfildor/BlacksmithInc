# First-day playtest script

For whoever runs the session. It checks one thing: whether a person who has never seen Tiny Blacksmith understands
their first day from the guided first day alone. About 20 minutes per person, five people (task T7.2).

## Before each session

1. Install the build under test: `./gradlew :app:installDebug`.
2. Make it a first launch. Either `adb shell pm clear com.example.blacksmithproject`, or on the phone: Settings, Apps,
   Tiny Blacksmith, Storage, Clear storage.
3. Open the game and stop on the title. Check that it reads "Era 1 awaits", "This game starts with a short guided
   first day." and "No charter". If it does not, the data was not cleared.
4. Leave the phone's font size and motion settings as the participant would have them. Note them down.
5. Record the screen if the participant agrees (`adb shell screenrecord`, or the phone's recorder).

The debug build shows two extra buttons on the title, "Scenarios (debug)" and "Combat sandbox (debug)". Say once:
"The two buttons marked debug are not part of the game." Say nothing else about the screen.

## What to say

Read this and nothing more:

> This is a game about running a blacksmith's shop. Please play it as you would at home, and say out loud what you
> are thinking. I am testing the game, not you. I cannot answer questions while you play, but I will write them down
> and answer them at the end.

Then hand over the phone.

## While they play

Do not explain, point, nod at a right answer or finish their sentence. If they ask what to do, say: "What would you
try?" If they are silent for a while, say: "What are you looking at?" If they are stuck for two full minutes, note
the time and the screen, then say: "You can stop here or go on, as you like."

Stop the session when they have ended day 2, or after 15 minutes of play, whichever comes first. Do not stop them at
"Done": what they do next is part of the test.

## What to write down

Write what they did and the words they used. Do not write what you think they meant.

| # | Watch for | Seen (yes / partly / no) | What they did or said |
|---|---|---|---|
| 1 | From the title to their first deliberate action: do they know what to do first? Time taken. | | |
| 2 | Before the first forge: which weapon, metal and augment, and any reason they give. | | |
| 3 | After the first forge: do they find the result and put it on the shelf? Do they change the price? | | |
| 4 | The second forge: did they forge one? Same recipe or different? Reason given, if any. | | |
| 5 | Do they find End Day unaided? | | |
| 6 | During the day's cards: what do they say is happening? | | |
| 7 | Day 2, after "Done": what is the first thing they do, and why? | | |
| 8 | "Skip guidance" or "Why?" pressed: when, and what they said. | | |
| 9 | Anything they tried that the game did not allow, and any moment they stopped and searched. | | |

Row 4 needs care. A second forge with a different metal or augment does not by itself show understanding: the step
says "Change the metal or the augment". Note which of these you saw:

- They changed something and said what they expected to differ.
- They changed something because the line said to.
- They repeated the same recipe.
- They skipped it and ended the day.

## Questions at the end

Ask these after they stop, in this order, in these words. Write the answer down as said. Do not correct a wrong
answer until all six are asked.

1. "What did you choose when you made your first weapon, and what did each choice change?"
2. "What did making it cost you?"
3. "If you made the same weapon again, would it come out the same?"
4. "When do heroes decide whether to buy?"
5. If a day passed without a sale: "Why do you think nothing sold?" If every day had a sale: "What would you do if a
   day passed and nothing sold?"
6. "What would you do next, if you kept playing?"

Then, and only then, opinions:

7. "Was anything confusing?"
8. "Was there anything you wanted the game to tell you that it did not?"
9. "What did you think of the hints at the top of the screen?"

## After each session

Keep three lists apart:

- **Observed.** What they did, with times. Rows 1 to 9.
- **Said in answer.** Questions 1 to 6. Mark each answer right, partly right or wrong against the rules below.
- **Opinions.** Questions 7 to 9, and anything they volunteered.

The rules the answers are marked against:

1. The weapon type, the metal and the augment. The metal sets strength, the augment sets the element; how well a pair
   works together is found by forging it.
2. Two energy of the day's ten, and one of each ingredient.
3. No. Quality lands near what the recipe gives, sometimes above and sometimes below.
4. At End Day, each on their own. A listed weapon is available to them; it is not sold until one of them buys it.
5. Any reason the game showed them: nothing was listed, no hero came in, or the reasons counted on the first-day
   strip and named in the Gazette under "All details".
6. Anything that follows from what they saw: the Shop's "Worth doing first", a cheaper price, another weapon, a
   different pairing.

## After all five

Report, for each of the six questions and each row, how many of five. Then list confusion points, each with how many
people hit it and what they did. Only then suggest changes, each tied to an observation, and say which opinions had
no observed behaviour behind them. A change that only one person's opinion supports is noted and not made.
