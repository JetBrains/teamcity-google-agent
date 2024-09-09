# TeamCity Google Cloud Agents 

[![official JetBrains project](http://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![plugin status]( 
https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:TeamCityGoogleCloudAgent_Build)/statusIcon.svg)](https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamCityGoogleCloudAgent_Build&guest=1)

This plugin allows TeamCity to scale the pool of build agents using cloud instances hosted in Google Compute Engine.

## Compatibility

The plugin is compatible with TeamCity 10.0.x and newer.

## Installation

[Download the plugin](https://plugins.jetbrains.com/plugin/9704-google-cloud-agents) and install it as an [additional TeamCity plugin](https://www.jetbrains.com/help/teamcity/installing-additional-plugins.html).

## Configuration

For configuration details please take a look at the [TeamCity Google Cloud plugins](https://blog.jetbrains.com/teamcity/2017/06/run-teamcity-ci-builds-in-google-cloud/) blog post.

The plugin supports Google Compute images to start new instances. You also need to create a new JSON private key and assign the `Compute Engine Instance Admin (v1)` and `Project Viewer` [roles](https://cloud.google.com/compute/docs/access/#predefined_short_product_name_roles) or create your own with a following permissions:

* `compute.images.list`
* `compute.instances.create`
* `compute.instances.list`
* `compute.instances.setMetadata`
* `compute.machineTypes.list`
* `compute.diskTypes.list`
* `compute.networks.list`
* `compute.subnetworks.list`
* `compute.zones.list`

**Note**: If you're using "Instance template" image type also assign a `Service Account User` role.

To verify whether your service account has all required permissions please enable [Google Cloud Resource Manager API](https://console.cloud.google.com/apis/api/cloudresourcemanager.googleapis.com/overview) in your project.

### Shared VPC permissions configuration

There is 3 ways to configure permissions for shared VPC:

1. In order to see networks and subnetworks in dropdowns in the "Add Image" dialog, you have to add the  `Compute Network User` role to the ServiceAccount under which VMs are created from TeamCity in the Host GCP project with Shared VPC.
2. You can select a limited number of subnetworks that your ServiceAccount should have access to. In Google Cloud Console go to the Host Project -> Shared VPC tab, select the subnet you want to grant access to and add the `Compute Network User` role to the account. In this case, you won't see networks and subnetworks (except for those that don't require any permissions), but you can specify subnet manually. The subnet must be specified in the following format:
`projects/[project_id]/regions/[region]/subnetworks/[subnet_id]`
    ![1.jpg](google-cloud-server/src/main/resources/readme/1.jpg)

    ![2.jpg](google-cloud-server/src/main/resources/readme/2.jpg)

    ![3.jpg](google-cloud-server/src/main/resources/readme/3.jpg)

3. In the case when it is necessary to show some specific subnets, first you need to do p.2, and then add the `Compute Network Viewer` role to the ServiceAccount in IAM to the Host Project with Shared VPC.

### Image Creation

Before you can setting up the integration, you need to create a new cloud image. To do that, create a new cloud instance, install the [TeamCity Build Agent](https://www.jetbrains.com/help/teamcity/teamcity-integration-with-cloud-solutions.html) on it and set it to start automatically. You also need to manually point the agent to the existing TeamCity server with the Google Cloud plugin installed to let the build agent download the plugins.

Then you need to [remove temporary files](https://confluence.jetbrains.com/display/TCDL/TeamCity+Integration+with+Cloud+Solutions#TeamCityIntegrationwithCloudSolutions-Capturinganimagefromavirtualmachine) and [create a new image](https://cloud.google.com/compute/docs/images/create-custom) from the instance disk.

### Setting Up TeamCity Cloud Profiles and Images

Once you have a custom Google image that can spawn identical cloud machines with TeamCity agents installed on them, set up TeamCity to be able to use this image. To do this, you need to set up a [cloud profile and an image](https://www.jetbrains.com/help/teamcity/agent-cloud-profile.html).

1. Go to the settings of a project that should be able to use your Google Cloud agents. This can be an individual or the `<Root>` project.
2. In the left navigation bar, click **Cloud Profiles**. A profile is an entity that specifies global cloud agent properties (such as the server URL, credentials that TeamCity should use to access your images, or the automatic terminate conditions).
3. Click **Create new profile** and choose "Google Compute Engine" in the **Cloud type** dropdown menu.
4. Fill in required settings and click **Add image**. An image contains more specific settings than its parent profile: the specific custom image that should be used, the maximum number of instances that can be spawned from this image, network settings, and more. A single cloud profile can host multiple cloud images.

    > Notes
    > 
    > * If you're using the "default" network and one of its "default" subnetworks, tick the "Specify subnetwork URL manually" checkbox in TeamCity cloud image settings. This checkbox allows you to explicitly specify the required network and avoid issues related to TeamCity attempting to access a network of a wrong region.
    >
    > * If you want to define custom [startup](https://cloud.google.com/compute/docs/startupscript) and [shutdown](https://cloud.google.com/compute/docs/shutdownscript) scripts, click **Edit metadata** link in the image settings.

5. Click **Save** and go to the **Agents** tab. Navigate to your newly created image and click **Start** to invoke a new Google Cloud-based build agent. This screen also allows you to monitor and shut down currently running cloud agents.

#### Preemptible instance

If you are using preemptible instances you have to specify [shutdown script](https://cloud.google.com/compute/docs/instances/create-start-preemptible-instance#handle_preemption) to gracefully reschedule build from a preempted VM on another build agent like that.

For Linix instances:
```json
{
  "shutdown-script": "#! /bin/bash\n/opt/buildagent/bin/agent.sh stop force"
}
```

For Windows instances:
```json
{
  "windows-shutdown-script-cmd": "C:\\BuildAgent\\bin\\agent.bat stop force"
}
```

## License

Apache 2.0

## Feedback

Please feel free to post feedback in the repository issues.
