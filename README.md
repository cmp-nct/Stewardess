# Stewardess
IntelliJ LivePlugin that adds functionality to Copilot by offering word-by-word completion

GitHub Copilot is a magnificent piece of software, however the plugins offer no configuration settings and no control over it's behavior.
In most cases Copilot will offer many lines of code when the developer just needs a little bit of code.
This results in a significant overhead, you are not only accepting 90% of garbage you also train the AI to deliver more garbage through it's telemetry responses.

This IntelliJ plugin solves the problem, it's a Stewardess for our Copilot
It does not change the way Copilot works, everything works as usual with one exception:
When using the additional shortcut the plugin will take the currently displayed suggestion and only write one word of it into the IDE.
It will simulate a user typing it, so Copilot will follow the typing without interruption.

Disclaimer:
I am not affiliated with Github or Copilot. I do not recommend to modify copilot's behavior itself. 
Anything that goes wrong is in your own responsibility. 

How to use:
Use Copilot as always just when ready to complete press the new shortcut.
By default: CTRL + ALT + D
Keep pressing the shortcut until you are done, you can always use the original shortcut to complete everything at once.

How to install:
1) Install "LivePlugin" addon. That's a Plugin that allows to add Kotlin code as plugin without much hassle
2) create a new Kotlin plugin and copy/paste this plugin into it
3) Run the plugin (you can auto-start it also)
4) CTRL+ALT+D will activate the completion
