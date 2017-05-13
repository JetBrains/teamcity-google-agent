# TeamCity Google Compute Engine plugin

Enables TeamCity cloud integration with Google Compute Engine and allows to use cloud instances to scale the pool of build agents.

## Installation

You can download the plugin install it as an [additional TeamCity plugin](https://confluence.jetbrains.com/display/TCDL/Installing+Additional+Plugins).

## Using

The plugin supports Google Compute images to start a new instances.

### Images Creation

Before you can start using integration, you need to create a new cloud image. The TeamCity Build Agent [must be installed](https://confluence.jetbrains.com/display/TCDL/TeamCity+Integration+with+Cloud+Solutions#TeamCityIntegrationwithCloudSolutions-PreparingavirtualmachinewithaninstalledTeamCityagent) and set to start automatically. Also, you need to manually point the agent to the existing TeamCity server with the Google Cloud plugin installed to let the build agent download the plugins.

> :grey_exclamation: If you plan to start agent as a Windows service under SYSTEM use `Automatic (Delayed Start)` startup type.

Then you should [remove temporary files](https://confluence.jetbrains.com/display/TCD9/TeamCity+Integration+with+Cloud+Solutions#TeamCityIntegrationwithCloudSolutions-Capturinganimagefromavirtualmachine) and create a new image from the instance disk.

## License

Apache 2.0

## Feedback

Please feel free to post feedback in the repository issues.
