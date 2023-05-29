import com.badlogic.gdx.*
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.utils.Clipboard

object Lwjgl3StubApplication: Application {
    private val files = Lwjgl3Files()

    override fun getApplicationListener(): ApplicationListener? {
        return null
    }

    override fun getGraphics(): Graphics? {
        return null
    }

    override fun getAudio(): Audio? {
        return null
    }

    override fun getInput(): Input? {
        return null
    }

    override fun getFiles(): Files {
        return files
    }

    override fun getNet(): Net? {
        return null
    }

    override fun log(tag: String?, message: String?) {
        // No implementation
    }

    override fun log(tag: String?, message: String?, exception: Throwable?) {
        // No implementation
    }

    override fun error(tag: String?, message: String?) {
        // No implementation
    }

    override fun error(tag: String?, message: String?, exception: Throwable?) {
        // No implementation
    }

    override fun debug(tag: String?, message: String?) {
        // No implementation
    }

    override fun debug(tag: String?, message: String?, exception: Throwable?) {
        // No implementation
    }

    override fun setLogLevel(logLevel: Int) {
        // No implementation
    }

    override fun getLogLevel(): Int {
        return 0
    }

    override fun setApplicationLogger(applicationLogger: ApplicationLogger?) {
        // No implementation
    }

    override fun getApplicationLogger(): ApplicationLogger? {
        return null
    }

    override fun getType(): Application.ApplicationType {
        return Application.ApplicationType.Desktop
    }

    override fun getVersion(): Int {
        return 1
    }

    override fun getJavaHeap(): Long {
        return 0
    }

    override fun getNativeHeap(): Long {
        return 0
    }

    override fun getPreferences(name: String?): Preferences? {
        return null
    }

    override fun getClipboard(): Clipboard? {
        return null
    }

    override fun postRunnable(runnable: Runnable?) {
        // No implementation
    }

    override fun exit() {
        // No implementation
    }

    override fun addLifecycleListener(listener: LifecycleListener?) {
        // No implementation
    }

    override fun removeLifecycleListener(listener: LifecycleListener?) {
        // No implementation
    }
}