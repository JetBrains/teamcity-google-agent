# TeamCity Google Compute Engine plugin
[![plugin status]( 
http://teamcity.jetbrains.com/app/rest/builds/buildType:TeamCityGoogleCloudAgent_Build,pinned:true/statusIcon.svg)](https://teamcity.jetbrains.com/viewLog.html?buildTypeId=TeamCityGoogleCloudAgent_Build&buildId=lastPinned&guest=1)

TeamCity integration with Google Compute Engine which allows to use cloud instances to scale the pool of build agents.

## Installation

You can [download the plugin](https://teamcity.jetbrains.com/app/rest/builds/buildType:TeamCityGoogleCloudAgent_Build,branch:default:any,tag:release/artifacts/content/cloud-google.zip?guest=1) and install it as an [additional TeamCity plugin](https://confluence.jetbrains.com/display/TCDL/Installing+Additional+Plugins).

## Configuration

The plugin supports Google Compute images to start a new instances. Also you need to create a new JSON private key and [assign role](https://cloud.google.com/compute/docs/access/#predefined_short_product_name_roles) `Compute Engine Instance Admin`.

### Image Creation

Before you can start using integration, you need to create a new cloud image. The TeamCity Build Agent [must be installed](https://confluence.jetbrains.com/display/TCDL/TeamCity+Integration+with+Cloud+Solutions#TeamCityIntegrationwithCloudSolutions-PreparingavirtualmachinewithaninstalledTeamCityagent) and set to start automatically. Also, you need to manually point the agent to the existing TeamCity server with the Google Cloud plugin installed to let the build agent download the plugins.

> :grey_exclamation: If you plan to start agent as a Windows service under SYSTEM use `Automatic (Delayed Start)` startup type.

Then you should [remove temporary files](https://confluence.jetbrains.com/display/TCD9/TeamCity+Integration+with+Cloud+Solutions#TeamCityIntegrationwithCloudSolutions-Capturinganimagefromavirtualmachine) and [create a new image](https://cloud.google.com/compute/docs/images/create-delete-deprecate-private-images) from the instance disk.

## License

Apache 2.0

## Feedback

Please feel free to post feedback in the repository issues.
