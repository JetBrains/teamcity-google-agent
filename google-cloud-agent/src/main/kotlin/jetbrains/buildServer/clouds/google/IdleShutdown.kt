

package jetbrains.buildServer.clouds.google

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.AgentLifeCycleAdapter
import jetbrains.buildServer.agent.AgentLifeCycleListener
import jetbrains.buildServer.agent.BuildAgentConfigurationEx
import jetbrains.buildServer.agent.impl.AgentIdleTimeTracker
import jetbrains.buildServer.util.EventDispatcher
import jetbrains.buildServer.util.RunCommand
import jetbrains.buildServer.util.executors.ExecutorsFactory
import java.text.MessageFormat
import java.util.concurrent.TimeUnit

/**
 * @author Sergey.Pak
 * *         Date: 10/28/2014
 * *         Time: 12:13 PM
 */
class IdleShutdown(private val myTracker: AgentIdleTimeTracker,
                   dispatcher: EventDispatcher<AgentLifeCycleListener>,
                   private val myConfig: BuildAgentConfigurationEx,
                   private val myRunCommand: RunCommand) {

    private val myService = ExecutorsFactory.newFixedScheduledDaemonExecutor("Google instance shutdown on idle time", 1)

    init {
        dispatcher.addListener(object : AgentLifeCycleAdapter() {
            override fun agentShutdown() {
                myService.shutdownNow()
            }
        })
    }

    fun setIdleTime(idleTime: Long) {

        LOG.info(MessageFormat.format("Agent will be automatically shutdown after {0} minutes of inactivity.", idleTime / 1000 / 60))

        val r = object : Runnable {
            override fun run() {
                val actualIdle = myTracker.idleTime
                if (actualIdle > idleTime) {
                    LOG.warn("Agent was idle for " + actualIdle / 1000 / 60 + " minutes. Cloud profile timeout was " + idleTime / 1000 / 60 + " minutes. Instance will be shut down")
                    shutdownInstance()
                    return
                }

                //Check again
                myService.schedule(this, 10 + idleTime - actualIdle, TimeUnit.MILLISECONDS)
            }
        }
        r.run()
    }

    private fun shutdownInstance() {
        LOG.info("To change this command define '" + SHUTDOWN_COMMAND_KEY +
                "' property with proper shutdown command in the buildAgent.properties file")

        for (cmd in shutdownCommands) {
            LOG.info("Shutting down agent with command: $cmd")

            myRunCommand.runCommand(cmd, RunCommand.LoggerOutputProcessor(LOG, "Shutdown"))
        }
    }

    private val shutdownCommands: Array<String>
        get() {
            val cmd = myConfig.configurationParameters[SHUTDOWN_COMMAND_KEY]
            if (cmd != null) {
                return arrayOf(cmd)
            }

            val os = System.getProperty("os.name")
            LOG.info("Shutdown instance commands for $os")

            val info = myConfig.systemInfo

            if (info.isUnix || info.isMac) {
                return arrayOf("shutdown -Ph now", "halt -p", "poweroff", "sudo shutdown -Ph now", "sudo halt -p", "sudo poweroff")
            }

            if (info.isWindows) {
                return arrayOf("shutdown -s -t 1 -c \"TeamCity Google Agent Instance shutdown on idle time\" -f", "shutdown /s /t 1 /c \"TeamCity Google Agent Instance shutdown on idle time\" /f")
            }
            LOG.warn("No command for shutdown. Add '$SHUTDOWN_COMMAND_KEY' property to the buildAgent.properties file with shutdown command")
            return emptyArray()
        }

    companion object {
        private val LOG = Logger.getInstance(IdleShutdown::class.java.name)
        private const val SHUTDOWN_COMMAND_KEY = "teamcity.agent.shutdown.command"
    }
}