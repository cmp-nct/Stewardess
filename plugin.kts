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
                for (line in inlay_lines)
                {
                    inlay_content += line+"\n"
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
                addCompletionWord(false) // first word is completed directly
            }

        } else
        {
            show("No completion visible, requesting completion ..")
            this.editorManager.showNextInlaySet(editor!!)
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
       // uses completion_storage, takes the first word, adds to the editor at caret position, removes the first word from completion_storage and moves carret to the right after the word

        if (this.completion_storage.size > 0)
        {
            //show("addCompletionWord start: "+gson.toJson(this.completion_storage));
            var word = ""
            while (word.length < 1)
            {
                // if no words left exit
                if (this.completion_storage.size < 1)
                {
                    // nothing left, fetch current inlay if available
                    this.assistCopilot(false)
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
                        // todo: caret on vertical (block,afterlineend types) inserts is not moved correctly
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
            }

        }

    }
}




var Stewardess = ClassStewardess();

registerAction(id = "Stewardess_call", keyStroke = "ctrl alt D")
{
    event: AnActionEvent -> Stewardess.assistCopilot(true); // takes the inlay completion and starts inserting it word by word
}
// this uses the internal array only, more efficient than the above in terms of performance
registerAction(id = "Stewardess_continue", keyStroke = "ctrl alt H") { event: AnActionEvent ->
    Stewardess.addCompletionWord(true); // only inserts one word from the completion storage, ignoring if copilot is still offering it
}
if (!isIdeStartup)
    show("Loaded Copilot's Stewardess (press 'ctrl alt D' when a completion shows up)")
