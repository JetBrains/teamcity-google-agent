<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="/include.jsp" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
</table>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<jsp:useBean id="cons" class="jetbrains.buildServer.clouds.google.GoogleConstants"/>
<jsp:useBean id="basePath" class="java.lang.String" scope="request"/>

<h2 class="noBorder section-header">Cloud Access Information</h2>

<script type="text/javascript">
    BS.LoadStyleSheetDynamically("<c:url value='${resPath}settings.css'/>");
</script>

<div id="google-setting" data-bind="validationOptions: {insertMessages: false}">

    <table class="runnerFormTable" data-bind="with: credentials()">
        <tr>
            <th><label for="${cons.accessKey}">JSON private key: <l:star/></label></th>
            <td>
                <textarea name="prop:${cons.accessKey}" class="longField"
                          data-bind="initializeValue: accessKey, textInput: accessKey">${propertiesBean.properties[cons.accessKey]}</textarea>
                <span class="smallNote">Specify the JSON private key.
                    <bs:help urlPrefix="https://cloud.google.com/storage/docs/authentication#generating-a-private-key"
                             file=""/><br/>
                    You need to assign <em>Compute Engine Instance Admin</em> role
                            <bs:help urlPrefix="https://cloud.google.com/compute/docs/access/#predefined_short_product_name_roles"
                                    file=""/>
                </span>
                <span class="error option-error" data-bind="validationMessage: accessKey"></span>
            </td>
        </tr>
        <tr>
            <td colspan="2" data-bind="with: $parent">
                <span data-bind="css: {hidden: !loadingResources()}"><i class="icon-refresh icon-spin"></i> Loading service data...</span>
                <span class="error option-error"
                      data-bind="text: errorResources, css: {hidden: loadingResources}"></span>
            </td>
        </tr>
    </table>

    <bs:dialog dialogId="GoogleImageDialog" title="Add Image" closeCommand="BS.GoogleImageDialog.close()"
               dialogClass="GoogleImageDialog" titleId="GoogleImageDialogTitle">
        <table class="runnerFormTable">
            <tr>
                <th><label for="${cons.sourceImage}">Image: <l:star/></label></th>
                <td>
                    <select name="${cons.sourceImage}" class="longField"
                            data-bind="options: sourceImages, optionsCaption: '<Select image>',
                             optionsText: 'text', optionsValue: 'id', value: image().sourceImage"></select>
                    <i class="icon-refresh icon-spin" data-bind="css: {invisible: !loadingResources()}"></i>
                    <span class="error option-error" data-bind="validationMessage: image().sourceImage"></span>
                </td>
            </tr>
            <tr>
                <th><label for="${cons.zone}">Zone: <l:star/></label></th>
                <td>
                    <select name="${cons.zone}" class="longField"
                            data-bind="options: zones, optionsText: 'text', optionsValue: 'id',
                             value: image().zone"></select>
                    <i class="icon-refresh icon-spin" data-bind="css: {invisible: !loadingResources()}"></i>
                    <span class="error option-error" data-bind="validationMessage: image().zone"></span>
                </td>
            </tr>
            <tr>
                <th><label for="${cons.vmNamePrefix}">Name Prefix: <l:star/></label></th>
                <td>
                    <input type="text" name="${cons.vmNamePrefix}" class="longField"
                           data-bind="textInput: image().vmNamePrefix"/>
                    <span class="smallNote">Unique name prefix to create resource groups</span>
                    <span class="error option-error" data-bind="validationMessage: image().vmNamePrefix"></span>
                </td>
            </tr>
            <tr>
                <th><label for="${cons.maxInstancesCount}">Instances Limit: <l:star/></label></th>
                <td>
                    <div>
                        <input type="text" name="${cons.maxInstancesCount}" class="longField"
                               data-bind="textInput: image().maxInstances"/>
                        <span class="smallNote">Maximum number of instances which can be started</span>
                        <span class="error option-error" data-bind="validationMessage: image().maxInstances"></span>
                    </div>
                </td>
            </tr>
            <tr>
                <th class="noBorder"></th>
                <td>
                    <input type="checkbox" name="${cons.preemptible}" data-bind="checked: image().preemptible"/>
                    <label for="${cons.preemptible}">Use preemptible VM Instances
                        <bs:help urlPrefix="https://cloud.google.com/preemptible-vms/" file=""/>
                    </label>
                </td>
            </tr>
            <tr>
                <th><label for="${cons.machineType}">Machine Type: <l:star/></label></th>
                <td>
                    <select name="${cons.machineType}" class="longField"
                            data-bind="options: machineTypes, optionsText: 'text', optionsValue: 'id',
                             value: image().machineType"></select>
                    <i class="icon-refresh icon-spin" data-bind="css: {invisible: !loadingResources()}"></i>
                </td>
            </tr>
            <tr>
                <th><label for="${cons.network}">Network: <l:star/></label></th>
                <td>
                    <select name="${cons.network}" class="longField"
                            data-bind="options: networks, optionsText: 'text', optionsValue: 'id',
                             value: image().network, css: {hidden: networks().length == 0}"></select>
                    <div class="longField inline-block" data-bind="css: {hidden: networks().length > 0}">
                        <span class="error option-error">No networks found in the project</span>
                    </div>
                    <i class="icon-refresh icon-spin" data-bind="css: {invisible: !loadingResources()}"></i>
                </td>
            </tr>
            <tr class="advancedSetting">
                <th><label for="${cons.agentPoolId}">Agent Pool:</label></th>
                <td>
                    <select name="prop:${cons.agentPoolId}" class="longField"
                            data-bind="options: agentPools, optionsText: 'text', optionsValue: 'id',
                        value: image().agentPoolId"></select>
                    <span id="error_${cons.agentPoolId}" class="error"></span>
                </td>
            </tr>
        </table>

        <div class="popupSaveButtonsBlock">
            <input type="submit" value="Save" class="btn btn_primary submitButton"
                   data-bind="click: saveImage, enable: image.isValid"/>
            <a class="btn" href="#" data-bind="click: closeDialog">Cancel</a>
        </div>
    </bs:dialog>

    <h2 class="noBorder section-header">Agent Images</h2>
    <div class="imagesOuterWrapper">
        <div class="imagesTableWrapper">
            <span class="emptyImagesListMessage hidden"
                  data-bind="css: { hidden: images().length > 0 }">You haven't added any images yet.</span>
            <table class="settings google-settings imagesTable hidden"
                   data-bind="css: { hidden: images().length == 0 }">
                <thead>
                <tr>
                    <th class="name">Name Prefix</th>
                    <th class="name">Source Image</th>
                    <th class="name center" title="Maximum number of instances">Limit</th>
                    <th class="name center" colspan="2">Actions</th>
                </tr>
                </thead>
                <tbody data-bind="foreach: images">
                <tr>
                    <td class="nowrap" data-bind="text: $data['source-id']"></td>
                    <td class="nowrap">
                        <span data-bind="text: sourceImage.slice(-80), attr: {title: sourceImage}"></span>
                    </td>
                    <td class="center edit" data-bind="text: maxInstances"></td>
                    <td class="edit">
                        <a href="#" data-bind="click: $parent.showDialog,
                        css: {hidden: !$parent.isValidCredentials() || $parent.loadingResources()}">Edit</a>
                    </td>
                    <td class="remove edit"><a href="#" data-bind="click: $parent.deleteImage">Delete</a></td>
                </tr>
                </tbody>
            </table>

            <c:set var="sourceImagesData" value="${propertiesBean.properties[cons.imagesData]}"/>
            <c:set var="imagesData" value="${propertiesBean.properties['images_data']}"/>
            <input type="hidden" name="prop:${cons.imagesData}"
                   value="<c:out value="${empty sourceImagesData || sourceImagesData == '[]' ? imagesData : sourceImagesData}"/>"
                   data-bind="initializeValue: images_data, value: images_data"/>
        </div>

        <a class="btn" href="#" disabled="disabled"
           data-bind="click: showDialog.bind($data, null), attr: {disabled: !isValidCredentials() || loadingResources() ? 'disabled' : null}">
            <span class="addNew">Add image</span>
        </a>
    </div>

</div>

<script type="text/javascript">
    BS.GoogleImageDialog = OO.extend(BS.AbstractModalDialog, {
        getContainer: function () {
            return $('GoogleImageDialog');
        },
        showDialog: function (addImage) {
            var action = addImage ? "Add" : "Edit";
            $j("#GoogleImageDialogTitle").text(action + " Image");
            this.showCentered();
        }
    });

    $j.when($j.getScript("<c:url value="${resPath}knockout-3.4.0.js"/>").then(function () {
            return $j.when($j.getScript("<c:url value="${resPath}knockout.validation-2.0.3.js"/>"),
                $j.getScript("<c:url value="${resPath}knockout.extenders.js"/>"));
        }),
        $j.getScript("<c:url value="${resPath}images.vm.js"/>")
    ).then(function () {
        var dialog = document.getElementById("google-setting");
        ko.applyBindings(new GoogleImagesViewModel($j, ko, "<c:url value='${basePath}'/>", BS.GoogleImageDialog), dialog);
    });
</script>
<table class="runnerFormTable">
