import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger
import liveplugin.*
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.markup.TextAttributes
import com.google.gson.GsonBuilder

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.Notifications.Bus
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget.TextPresentation
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl


// depends-on-plugin com.github.copilot
import com.github.copilot.util.ApplicationUtil
import com.github.copilot.editor.CopilotEditorManagerImpl
import com.github.copilot.editor.CopilotEditorManager
import com.github.copilot.editor.CopilotEditorUtil
import com.github.copilot.util.ApplicationUtil.findCurrentProject
import com.github.copilot.editor.CopilotCommandListener
import com.github.copilot.util.EditorUtilCopy
import com.intellij.openapi.editor.Editor
import com.github.copilot.request.EditorRequest
import com.github.copilot.completions.CopilotCompletionService;
import com.github.copilot.completions.CopilotInlayList
import com.github.copilot.editor.CopilotInlayRenderer
import com.github.copilot.completions.CopilotCompletionType
//import com.github.copilot.editor.EditorRequestResultList // private package
//import com.github.copilot.completions.DefaultInlayList // implements CopilotInlayList .. again private ..
import com.github.copilot.status.CopilotStatus
import com.github.copilot.status.CopilotStatusService
import com.intellij.openapi.util.Pair

import java.util.Timer
import kotlin.concurrent.schedule
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import javax.swing.JLabel
import javax.swing.JComponent
import java.awt.Color
import java.awt.Font



import com.github.copilot.*

fun<T: Any> T.accessField(fieldName: String): Any? {
    return javaClass.getDeclaredField(fieldName).let { field ->
        field.isAccessible = true
        return@let field.get(this)
    }
}
val gson = GsonBuilder().setPrettyPrinting().create()


class copilot {
    companion object {
        fun getEditorManager(): CopilotEditorManagerImpl {
            return CopilotEditorManagerImpl()
        }
        //  create companion method for applicationUtil.findCurrentProject();
        fun findCurrentProject(): Project? {
            return ApplicationUtil.findCurrentProject()
        }
        // create companion method for com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
        fun getFileEditorManager(project: Project): FileEditorManager {
            return FileEditorManager.getInstance(project)
        }
        // create companion method for editor.getInlayModel()
        fun getInlayModel(editor: Editor): InlayModel {
            return editor.getInlayModel()
        }
        // create companion method for com.github.copilot.editor.CopilotEditorUtil.isSelectedEditor(editor)
        fun isSelectedEditor(editor: Editor): Boolean {
            return CopilotEditorUtil.isSelectedEditor(editor)
        }

        fun getStatusService(): CopilotStatusService {
            return CopilotStatusService()
        }
        fun StatusNotify(status: CopilotStatus, message: String?) {
            return CopilotStatusService.notifyApplication(status, message)
        }
        fun getCurrentStatus(): Pair<CopilotStatus, String> {
            return CopilotStatusService.getCurrentStatus()
        }
        // Get the Pair





    }

}




var project = copilot.findCurrentProject()
var commandListener = CopilotCommandListener(project!!)
var FILE_EDITOR_MANAGER = copilot.getFileEditorManager(project!!)
var editor = FILE_EDITOR_MANAGER.getSelectedTextEditor()



inline fun <reified T> Any.get_members(name: String):T {
    val allFields = javaClass.allSuperClasses().flatMap { it.declaredFields.toList() }
    val fieldClass = T::class.java
    var list="get_members() for "+name+": " //
    val field = allFields.find { list+=it.name+","; (it.name == name && (fieldClass.isAssignableFrom(it.type))&&false) }
return list as T;
}



if (!copilot.isSelectedEditor(editor!!))
    editor = FILE_EDITOR_MANAGER.getSelectedTextEditor() // quick test if the current editor is still selected, fix in case


class ClassStewardess {
// define a global public  class variable that will hold an array of strings
public var completion_storage: Array<String> = arrayOf("")
public var inlayModel: InlayModel = copilot.getInlayModel(editor!!)
//editorManager
public var editorManager: CopilotEditorManagerImpl = copilot.getEditorManager()
var circular_call = 0

    // action_call is true when a user requested this call, false when it was internally called
    fun assistCopilot(action_call: Boolean)
    {
        if (action_call)
        {
            // reset
            this.circular_call = 0
            this.completion_storage = arrayOf("")
            //show("Initiated Copilot's Stewardess assist")
        }
        //val project = event.project ?: return // Can be null if there are no open projects.
        //val editor = event.editor ?: return // Can be null if focus is not in the editor or no editors are open.
        val project = copilot.findCurrentProject()
        var editor = FILE_EDITOR_MANAGER.getSelectedTextEditor()

        var caretOffset = editor!!.getCaretModel().getOffset()
        var line_number = editor!!.getDocument().getLineNumber(caretOffset) // not sure why +1 is needed
        var end_offset = editor!!.getDocument().getLineEndOffset(line_number)
        if (end_offset > editor!!.getDocument().getTextLength()) end_offset = editor!!.getDocument().getTextLength()
        var start_offset = editor!!.getDocument().getLineStartOffset(line_number-1)
        //var caretOffsetAfterTab = EditorUtilCopy.indentLine(project, editor!!, line_number, 4, caretOffset) // intend line offset ?
        //var inlays = this.inlayModel.getInlineElementsInRange(editor!!.getDocument().getLineStartOffset(line_number), editor!!.getDocument().getLineEndOffset(line_number+1))
        var has_completions = this.editorManager.hasCompletionInlays(editor!!) // returns true if there are any copilot inlays, manager also can do applyCompletion(editor)
        if (has_completions)
        {

            var KEY_LAST_REQUEST = this.editorManager.accessField("KEY_LAST_REQUEST") as Key<*> // val
            // KEY_LAST_REQUEST ist ein Key<"copilot.editorRequest">, com.github.copilot.request.EditorRequest

            var request = KEY_LAST_REQUEST.get(editor)  // = EditorRequestResultList, contains inlayLists. Aber .get() geht auf com.github.copilot.completions.CopilotInlayList
            // public List<CopilotInlayRenderer> collectInlays(@NotNull final Editor editor, final int startOffset, final int endOffset) {
            start_offset = editor!!.getCaretModel().getOffset()
            // get number of lines in document
            var line_count = editor!!.getDocument().getLineCount()
            // get offset for after line_number+10 , if it's not higher than the document length
            var line_goal = line_number+10
            if (line_goal > line_count) line_goal = line_count-1
            var end_offset = editor!!.getDocument().getLineEndOffset(line_goal)

            var inlay_found = this.editorManager.collectInlays(editor!!, start_offset-1, end_offset)
            if (inlay_found.size <=0)
            {
            // debugging output
                //show("start_offset: "+start_offset + " end_offset: "+end_offset + " line_count: "+line_count + " line_goal: "+line_goal + " line_number: "+line_number + " caretOffset: "+caretOffset +  " has_completions: "+has_completions)
                inlay_found = this.editorManager.collectInlays(editor!!, 0, editor!!.getDocument().getTextLength())
                if (inlay_found.size > 0)
                {
                    //show("inlay_found: "+inlay_found + " at offset max " + editor!!.getDocument().getTextLength())
                    return
                }

                return
            }


            // [CopilotDefaultInlayRenderer(lines=[is a list of CopilotInlayRenderer], content=is a list of CopilotInlayRenderer, type=Inline, textAttributes=[java.awt.Color[r=2,g=160,b=255],null,0,BOXED,java.awt.Color[r=34,g=161,b=210],{},null], cachedWidth=231, cachedHeight=-1)]
            // get first inlay, if one exists, and extract content, lines, type
            //foreach inlay_found add content to array of strings
            var inlay_content_array = arrayOf("")
            for (inlay in inlay_found)
            {
                var inlay_content = inlay.accessField("content") as String  // a string
                inlay_content_array += inlay_content
            }
            //show("Array if inlays: "+gson.toJson(inlay_content_array))
            // if first inlay is empty, remove it
            if (inlay_content_array[0] == "")
            {
                inlay_content_array = inlay_content_array.drop(1).toTypedArray()
                //show("Array if inlays(fix): "+gson.toJson(inlay_content_array))
            }

            if (inlay_found.size > 0)
            {
                var inlay = inlay_found[0]
                //var inlay_content = inlay.accessField("content") as String  // a string
                var inlay_lines = inlay.accessField("lines") as List<String> // is a ["string"]
                //var inlay_lines = inlay.accessField("lines") as List<CopilotInlayRenderer> // is a ["string"]
                var inlay_type = inlay.accessField("type") as CopilotCompletionType // "Inline"
                // go through lines and build inlay_content, adding a newline per line, this seems to be required to get proper completion
                var inlay_content = ""
                // if there are multiple lines in the inlay, add a newline per line if not already present
                if (inlay_lines.size > 1 || inlay_found.size > 1)
                {
                // The first inlay is typically one line, the second one contains multiple lines of the entire next block
                    for (inlay in inlay_found)
                    {
                        inlay_lines = inlay.accessField("lines") as List<String> // is a ["string"]
                        for (line in inlay_lines)
                        {
                        // show( inlay number, line number, line content
                        show("DEBUG inlay: "+inlay_found.indexOf(inlay) + " line: "+inlay_lines.indexOf(line) + " content: '"+line+"'")
                            if (inlay_lines.indexOf(line) > 0 && !line.endsWith("\n"))
                            {
                                inlay_content += line+"\n"
                            } else
                            {
                                inlay_content += line
                            }
                        }
                    }
                    /*for (line in inlay_lines)
                    {
                        if (!line.endsWith("\n"))
                        {
                            inlay_content += line+"\n"
                        }  else
                        {
                          inlay_content += line
                        }
                    }*/
                } else
                {
                    inlay_content = inlay_lines[0]
                }





                //show("Lines:"+gson.toJson(inlay_lines))
                //var inlay_textAttributes = inlay.accessField("textAttributes") as TextAttributes //[java.awt.Color[r=2,g=160,b=255],null,0,BOXED,java.awt.Color[r=34,g=161,b=210],{},null]
                //var inlay_cachedWidth = inlay.accessField("cachedWidth") as Int
                //var inlay_cachedHeight = inlay.accessField("cachedHeight") as Int
                // split inlay into inlay_content_words using whitespace as separator, include the delimiters in the words by using lookahead and lookbehind
                var splitter = arrayOf("[.\\n]", "[\\s]") // this must include a whitespace, otherwise the split will not work
                // regex for all entries in splitter using this template: (?<=X)|(?=X) where X is the entry in splitter
                var splitter_regex = splitter.map { "(?<=" + it + ")|(?=" + it + ")" }.joinToString(separator = "|")
                inlay_content = inlay_content + ' '; // add whitespace to end of inlay_content, so that the last word is also split
                var inlay_content_words = inlay_content.split(Regex(splitter_regex)) // (?<=\\s)|(?=\\s)|(?<=,)|(?=,)

                // go through the words, if they are one of the regex characters in splitter then add them to the start of the next word. Use regex to match splitter characters
                var inlay_content_words_cleaned = mutableListOf<String>()
                var last_word = ""
                for (word in inlay_content_words)
                {
                    var splitter_match  = splitter.any { word.matches(Regex('^'+it+'$')) }
                    if (splitter_match)
                    {
                        last_word += word
                    }
                    else
                    {
                        if (last_word != "") inlay_content_words_cleaned.add(last_word)
                        last_word = word
                    }
                }
                this.completion_storage = inlay_content_words_cleaned.toTypedArray()
                // remove the ' ' from the end of the last word
                var tmp =  this.completion_storage.last()
                if (tmp.endsWith(' ')) tmp = tmp.substring(0, tmp.length-1)
                this.completion_storage[this.completion_storage.size-1] = tmp // fix the hacky whitespace addition
                // if the last word is empty, remove it
                // if this.completion_storage has more entries than one \n character or whitespace then addcompletionWord()

                 // if this.completion_storage last entry is a newline, remove it too
                //if (this.completion_storage.last() == "\n") this.completion_storage = this.completion_storage.dropLast(1).toTypedArray()
                // if this.completion_storage has one or more entries, continue
                this.completion_storage = this.completion_storage.filter { it != "" }.toTypedArray() // remove empty entries, wherever they came from
                if (this.completion_storage.size > 0)
                    addCompletionWord(false) // first word is completed directly
            } // this

        } else
        {
            show("No completion visible, requesting completion ..")
            this.editorManager.showNextInlaySet(editor!!)
        }


// this is
        return
    }
    // public function that will add one completion word into the editor
    fun addCompletionWord(action_call: Boolean)
    {
      if (action_call)
      {
          // reset circular protection
          this.circular_call = 0
      }
       // uses completion_storage, takes the first word, adds to the editor at caret position, removes the first word from completion_storage and moves carret to the right after the word

        if (this.completion_storage.size > 0)
        {
            show("addCompletionWord start: "+gson.toJson(this.completion_storage) + "size: "+this.completion_storage.size);
            var word = ""
            while (word.length < 1)
            {
                // if no words left exit
                if (this.completion_storage.size < 1)
                {
                    // nothing left, fetch current inlay if available
                    if (this.circular_call < 20)
                    {
                        this.circular_call += 1
                        this.assistCopilot(false)
                    } else
                    {
                        show("Circular call protection triggered, exiting 1")
                    }
                    return
                }
                word = this.completion_storage[0]
                completion_storage = completion_storage.drop(1).toTypedArray()
            }

            editor = FILE_EDITOR_MANAGER.getSelectedTextEditor()
            editor!!.document.executeCommand(project!!, description = "Stewardess is typing")
            {
                    // insert the whole word, character by character to seamlessly integrate with copilots feature to follow manual typing
                    for (i in 0..word.length-1)
                    {
                        //show(gson.toJson(word[i]))
                        insertString(editor!!.caretModel.offset, word[i].toString())
                        editor!!.getCaretModel().moveCaretRelatively(1, 0, false, false, true)
                    }

                }

        }
        else
        {
            show("Copilot's Stewardess: No more completion words available - retrying "+ this.circular_call)
            if (this.circular_call < 20)
            {
                this.circular_call += 1
                this.assistCopilot(false)
            } else
            {
                show("Circular call protection triggered, exiting 2")
            }

        }

    }


    // manage an intellij notificationGroup, add the current copilot status into it
    public var previous_status: CopilotStatus? = CopilotStatus.Unsupported
    public var status_is_running: Boolean = false
    public var hints_shown_cycles: Int = 0

    fun update_status(called_by_user: Boolean)
    {
        var copilot_status = "NOTIFICATION"
        if (called_by_user)
        {
            this.status_is_running = !this.status_is_running
            if (this.status_is_running)
            {
                show("Copilot's Stewardess: Status updates enabled")
            }
            else
            {
                show("Copilot's Stewardess: Status updates disabled")
                // throw exception
                throw Exception("Copilot's Stewardess: Status updates disabled (hack to disable notification)")
            }

        }
        if(!this.status_is_running) return
        if (this.hints_shown_cycles > 0)
        {
            this.hints_shown_cycles = this.hints_shown_cycles + 1
            if (this.hints_shown_cycles > 5)
            {
                SwingUtilities.invokeLater(Runnable {
                                this.hints_shown_cycles=0
                                HintManager.getInstance().hideAllHints()

                            })
            }
        }


        //var notificationGroup = NotificationGroup.balloonGroup("me.copilot.stewardess", "Stewardess");
        //var notificationGroup_tool = NotificationGroup("me.copilot.stewardess", NotificationDisplayType.TOOL_WINDOW, true);
        //Bus.notify(notificationGroup.createNotification("Copilot Status", copilot_status, NotificationType.INFORMATION));

        //test.getCurrentStatus()
        var current_status = copilot.getCurrentStatus().getFirst() as CopilotStatus?;
        var current_status_string = when (current_status) {
            CopilotStatus.Ready -> "Ready"
            CopilotStatus.NotSignedIn -> "NotSignedIn"
            CopilotStatus.CompletionInProgress -> "CompletionInProgress"
            CopilotStatus.AgentWarning -> "AgentWarning"
            CopilotStatus.AgentError -> "AgentError"
            CopilotStatus.AgentBroken -> "AgentBroken"
            CopilotStatus.IncompatibleClient -> "IncompatibleClient"
            CopilotStatus.Unsupported -> "Unsupported"
            CopilotStatus.UnknownError -> "UnknownError"
            else -> "Unknown"
        }
        if (this.previous_status != current_status)
        {
            //HintManager.getInstance().showInformationHint(editor!!, "Copilot Status: "+current_status_string)
            // call this on Event Dispatch Thread (EDT)
            SwingUtilities.invokeLater(Runnable {
                    var jComponent = JLabel("Copilot Status: "+current_status_string)
                    jComponent.isOpaque = true
//                    jComponent.background = Color(200, 200, 255, 128)
                    // smaller font
                    jComponent.font = Font("Arial", Font.PLAIN, 9)
                    HintManager.getInstance().showInformationHint(editor!!, jComponent)
                    //
                this.hints_shown_cycles=1
//                HintManager.getInstance().showInformationHint(editor!!, "Copilot Status: "+current_status_string)

            })

    // Solution to



            this.previous_status = current_status
        }




    }




 


}

var Stewardess = ClassStewardess();

// write in kotlin code
AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay({     Stewardess.update_status(false) }, 100, 250, TimeUnit.MILLISECONDS);

//Timer().schedule(1000) {
//     Stewardess.update_status()
//}
// kotlin:
// this a

//AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay({     Stewardess.update_status() }, 0, 1, TimeUnit.SECONDS);


registerAction(id = "Stewardess_test", keyStroke = "ctrl shift D")
{
    event: AnActionEvent -> Stewardess.update_status(true);
}
registerAction(id = "Stewardess_call", keyStroke = "ctrl alt D")
{
    event: AnActionEvent -> Stewardess.assistCopilot(true); // takes the inlay completion and starts inserting it word by word
}
// this uses the internal array only, more efficient than the above in terms of performance
registerAction(id = "Stewardess_continue", keyStroke = "ctrl alt H") { event: AnActionEvent ->
    Stewardess.addCompletionWord(true); // only inserts one word from the completion storage, ignoring if copilot is still offering it
}
if (!isIdeStartup)
    show("Loaded Copilot's Stewardess (press 'ctrl alt D' when a completion shows up, 'ctrl shift d' will enable/disable copilot status updates)")



