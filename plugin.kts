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
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.util.concurrent.atomic.AtomicBoolean

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.editor.ScrollType;


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

    }

}




var project = copilot.findCurrentProject()
var commandListener = CopilotCommandListener(project!!)
var FILE_EDITOR_MANAGER = copilot.getFileEditorManager(project!!)
var editor = FILE_EDITOR_MANAGER.getSelectedTextEditor()
var DEBUG_LEVEL=0


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
            var KEY_LAST_REQUEST = this.editorManager.accessField("KEY_LAST_REQUEST") as Key<*>
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
                if (DEBUG_LEVEL > 2) show("start_offset: "+start_offset + " end_offset: "+end_offset + " line_count: "+line_count + " line_goal: "+line_goal + " line_number: "+line_number + " caretOffset: "+caretOffset +  " has_completions: "+has_completions)
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

                // The first inlay is typically one line, the second one contains multiple lines of the entire next block
                for (inlay in inlay_found)
                {
                    var inlay_index = inlay_found.indexOf(inlay)
                    inlay_type = inlay.accessField("type") as CopilotCompletionType
                    for (line in inlay_lines)
                    {
                        if (inlay_type == CopilotCompletionType.Inline)
                        {
                            // inline inlays seem to always end with the line - there is a problem when the line is not empty after caret position, line might need to be cleaned to avoid double inserts (also an error of copilot)
                            if (inlay_index > 0)
                            {
                                if (!inlay_content.endsWith("\n")) inlay_content += "\n"
                                if (DEBUG_LEVEL > 1) show("DEBUG INLINE inlay: index > 0 - adding newline but not handling it now")
                                continue
                            }
                            if (DEBUG_LEVEL > 1)show("DEBUG INLINE inlay : "+inlay_found.indexOf(inlay) + " line: "+inlay_lines.indexOf(line) + " content:"+gson.toJson(line))
                            inlay_content += line
                        } else
                        if (inlay_type == CopilotCompletionType.Block)
                        {
                            if (inlay_index > 0)
                            {
                                if (!inlay_content.endsWith("\n")) inlay_content += "\n"
                                if (DEBUG_LEVEL > 1)show("DEBUG BLOCK inlay: index > 0 - adding newline but not handling it now")
                                continue
                            } else
                            {
                                // it's the first inlay and it's a block inlay. For some reason we need to add a starting newline
                                if (!inlay_content.startsWith("\n")) inlay_content = "\n" + inlay_content
                            }
                            if (DEBUG_LEVEL > 1)show("DEBUG BLOCK inlay : "+inlay_found.indexOf(inlay) + " line: "+inlay_lines.indexOf(line) + " content:"+gson.toJson(line))
                            // not sure - with BLOCK inlays we just ensure that each line ends with a newline
                            // block inserts inside comments could be fixed be avoiding the */ bug at the end (todo)

                            if (inlay_lines.indexOf(line) > 0 && !line.endsWith("\n"))
                            {
                                inlay_content += line+"\n"
                            } else
                            {
                                inlay_content += line
                            }

                        } else
                        if (inlay_type == CopilotCompletionType.AfterLineEnd)
                        {
                            // not seen in the wild yet
                            if (DEBUG_LEVEL > 0)show("DEBUG AfterLineEnd inlay : "+inlay_found.indexOf(inlay) + " line: "+inlay_lines.indexOf(line) + " content:"+gson.toJson(line))
                            inlay_content += line
                        }
                    }


                }
// this is the content of the inlay, it's a string

                //var inlay_textAttributes = inlay.accessField("textAttributes") as TextAttributes //[java.awt.Color[r=2,g=160,b=255],null,0,BOXED,java.awt.Color[r=34,g=161,b=210],{},null]
                //var inlay_cachedWidth = inlay.accessField("cachedWidth") as Int
                //var inlay_cachedHeight = inlay.accessField("cachedHeight") as Int
                // split inlay into inlay_content_words using whitespace as separator, include the delimiters in the words by using lookahead and lookbehind
                var splitter = arrayOf("[.,\\n\\s()=+-]") // this must include a whitespace, otherwise the split will not work
                // regex for all entries in splitter using this template: (?<=X)|(?=X) where X is the entry in splitter
                var splitter_regex = splitter.map { "(?<=" + it + ")|(?=" + it + ")" }.joinToString(separator = "|")
                inlay_content = inlay_content + ' '; // add whitespace to end of inlay_content, so that the last word is also split (arraylist)
                var inlay_content_words = inlay_content.split(Regex(splitter_regex)) as ArrayList<String>
                // remove the one trailing whitespace from the last word
                inlay_content_words[inlay_content_words.size-1] = inlay_content_words[inlay_content_words.size-1].dropLast(1)

                // go through all words creating inlay_content_words_cleaned:
                // if length of word is 0, skip it
                // if the word solely consists of \t or ' ': skip it but remember it and prepend it to the next word
                // if word exactly matches (regex) one of the splitter delimiters: skip it but remember it and prepend it to the next word

                var inlay_content_words_cleaned = arrayOf("")
                var inlay_content_words_cleaned_index = 0
                var inlay_content_words_cleaned_last_word = ""
                var cleaned_word = ""
                for (word in inlay_content_words)
                {
                    if (word.length == 0)
                    {
                        continue
                    }
                    if (DEBUG_LEVEL >2)
                        show ("DEBUG processing word: "+gson.toJson(word.toCharArray()))
                    if (word.matches(Regex('^'+"[\\t ]+"+'$')))
                    {
                        inlay_content_words_cleaned_last_word = inlay_content_words_cleaned_last_word + word
                        continue
                    }
                    var splitter_match  = splitter.any { word.matches(Regex('^'+it+'$')) }
                    if (splitter_match)
                    {
                        inlay_content_words_cleaned_last_word = inlay_content_words_cleaned_last_word + word
                        continue
                    }
                    cleaned_word = inlay_content_words_cleaned_last_word + word
                    //show("added cleaned word: "+gson.toJson(cleaned_word.toCharArray()));
                    inlay_content_words_cleaned[inlay_content_words_cleaned_index] = cleaned_word
                    inlay_content_words_cleaned_last_word = ""
                    inlay_content_words_cleaned_index += 1
                    inlay_content_words_cleaned += ""
                }
                if (inlay_content_words_cleaned_last_word.length > 0)
                {
                    // we ended with a delimiter collection that was not added to the last word
                    inlay_content_words_cleaned[inlay_content_words_cleaned_index] = inlay_content_words_cleaned_last_word
                }
                // remove last empty entry if it is empty
                if (inlay_content_words_cleaned[inlay_content_words_cleaned.size-1].length == 0)
                {
                    //inlay_content_words_cleaned = inlay_content_words_cleaned.dropLast(1).toTypedArray()
                    inlay_content_words_cleaned = inlay_content_words_cleaned.sliceArray(0..inlay_content_words_cleaned.size-2)

                }
                this.completion_storage = inlay_content_words_cleaned

// this is the content of the inlay, it's an array of strings (words)
//if (inlay_type ==
//                if (inlay_type == CopilotCompletionType.Block)



                if (this.completion_storage.size > 0)
                {
                    addCompletionWord(false) // first word is completed directly
                }

            }

        } else
        {
            show("No completion visible, requesting completion ..")
            this.editorManager.showNextInlaySet(editor!!)
            // todo: instead trigger the request completion instead, should be native call and works better
        }




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

        if (this.completion_storage.size > 0)
        {
            var word = ""
            // not necessary anymore I guess, empty words are solved
            while (word.length == 0)
            {
                // if no words left exit
                if (this.completion_storage.size == 0)
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
            editor!!.document.executeCommand(project!!, description = "Stewardess is typing for the Copilot")
            {
                    // insert the whole word, character by character to seamlessly integrate with copilots feature to follow manual (non bulk) typing
                    // after a newline was inserted, then skip all prefixed whitespaces and tabs of the next word
                    // this is required because Copilot already handles intendation but our \n triggers the IDE to add intendation as well

                   var editor = FILE_EDITOR_MANAGER.getSelectedTextEditor()
                    var document = editor!!.document
                    var start_offset=0

                    var skip_prefix_whitespaces = false
                    for (i in 0..word.length-1)
                    {
                        if (skip_prefix_whitespaces)
                        {
                            if (word[i] == ' ' || word[i] == '\t')
                            {
                                continue
                            } else
                            {
                                skip_prefix_whitespaces = false
                            }
                        }
                        if (word[i] == '\n')
                        {
                            //skip_prefix_whitespaces = true  // disabled
                        }
                        // show( the character we insert as json
                        if (DEBUG_LEVEL > 2)show("DEBUG inserting character: "+gson.toJson(Character.toString(word[i])))
                        // todo: the last word should be REPLACED into the document IF there are characters after the caret
                        try
                        {
                            start_offset = editor!!.caretModel.offset;
                            document.insertString(start_offset, Character.toString(word[i]))

                            //insertString(editor!!.caretModel.offset, Character.toString(word[i]))
                            editor!!.getCaretModel().moveToOffset(start_offset + Character.toString(word[i]).length);
                            if (word[i] == '\n')
                            {
                                // move caret to begin of current line
                                editor!!.getCaretModel().moveToOffset(document.getLineStartOffset(document.getLineNumber(start_offset+1)))
                            }

                        //  editor!!.getCaretModel().moveCaretRelatively(1, 0, false, false, true)
                        } finally
                        {
                            // save ?
                            editor!!.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
                        }

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

    // Solution to show a notification in the tool window
    //



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



