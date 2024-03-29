# Stewardess
IntelliJ LivePlugin plugin that adds functionality to Copilot by offering word-by-word completion
I am not a Kotlin/Java developer, Copilot was a great help to create this project.

GitHub Copilot is a magnificent piece of software, however the plugins offer no configuration settings and no control over it's behavior.
In most cases Copilot will offer many lines of code when the developer just needs a little bit of code.
This results in a significant overhead, you are not only accepting 90% of garbage you also train the AI to deliver more garbage through it's telemetry responses.

This IntelliJ plugin solves the problem, it's a Stewardess for our Copilot
It does not change the way Copilot works, everything works as usual with one exception:
When using the additional shortcut the plugin will take the currently displayed suggestion and only write one word of it into the IDE.
It will simulate a user typing it, so Copilot will follow the typing without interruption.
![word-by-word-v1 1-demo](https://user-images.githubusercontent.com/78893154/197048317-34b0d526-69ee-446c-9f3d-4fd69ee9533a.gif)


Disclaimer:
I am not affiliated with Github or Copilot. I do not recommend to modify copilot's behavior itself. 
Anything that goes wrong is in your own responsibility. 
I'm not a Kotlin or Java or Plugin developer, so the code might not be as flawless as it could be.

How to use:
Use Copilot as always just when ready to complete press the new shortcut.
By default: CTRL + ALT + D
Keep pressing the shortcut until you are done, you can always use the original shortcut to complete everything at once.

How to install:
1) Install "LivePlugin" addon https://github.com/dkandalov/live-plugin. That's a Plugin that allows to add Kotlin code as plugin without much hassle
2) create a new Kotlin plugin and copy/paste this plugin into it
3) Run the plugin (you can auto-start it also)
4) CTRL+ALT+D will activate the completion
5) CTRL+SHIFT+D will switch ON/OFF the Copilot Status indicator

Known issues:
1) There is a bug in some combinations of word endings like `'xxx']);` it can have an issue with the separators, a bug in the regex somewhere.
2) Sometimes it does not see a completion, copilot has multiple internal representations of the same thing. I believe one of the updates activated one representation that was rarely used. It just means that the word-by-word completion won't work in that line.
3) Over the course of a few days the plugin stops to see completions at all, it's likely a deeper flaw not within my code. If that happens just reload the plugin.

Contribute:
I added a couple milestones that would advance this further.
I think there is a lot that can be improved so contributors are welcome.
Fixing one of the above bugs would be pleasant, I currently don't have time.
