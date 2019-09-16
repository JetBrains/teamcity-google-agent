/*
 * Copyright 2000-2017 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

function GoogleImagesViewModel($, ko, dialog, config) {
    var self = this;

    self.loadingResources = ko.observable(false);
    self.loadingResourcesByZone = ko.observable(false);
    self.validatingKey = ko.observable(false);
    self.errorResources = ko.observable("");
    self.showAccessKey = ko.observable(false);
    self.showMetadata = ko.observable(false);
    self.showServiceAccount = ko.observable(false);
    self.isDragOver = ko.observable(false);
    self.hasFileReader = ko.observable(typeof FileReader !== "undefined");

    // Credentials
    self.credentials = ko.validatedObservable({
        type: ko.observable(),
        accessKey: ko.observable().extend({required: true}).extend({
            validation: {
                async: true,
                validator: function (accessKey, otherVal, callback) {
                    var url = getBasePath() + "resource=permissions";
                    self.validatingKey(true);

                    $.post(url, {
                        "prop:secure:accessKey": accessKey
                    }).then(function (response) {
                        var $response = $(response);
                        var errors = getErrors($response);
                        if (errors) {
                            callback({isValid: false, message: errors});
                        } else {
                            callback(true);
                        }
                    }, function (error) {
                        callback({isValid: false, message: error.message});
                    }).always(function () {
                        self.validatingKey(false);
                    });
                },
                message: 'Invalid key'
            }
        })
    });

    self.credentials().type.subscribe(function (value) {
        if (value) self.loadInfo();
    });

    self.isValidCredentials = ko.pureComputed(function () {
        return self.credentials().type() === 'environment' ||
            self.credentials().type() === 'key' && self.credentials().accessKey.isValid();
    });

    self.isValidCredentials.subscribe(function (value) {
        if (value) self.loadInfo();
    });

    var imageTypes = {
        image: 'Image',
        template: 'Template'
    };

    self.imageTypes = ko.observableArray([
        {id: imageTypes.image, text: "Image"},
        {id: imageTypes.template, text: "Instance Template"}
    ]);

    self.imageType = ko.observable();

    // Image details
    var maxLength = 60;
    self.machineCustom = ko.observable(false);
    self.image = ko.validatedObservable({
        imageType: self.imageType,
        sourceImage: ko.observable().extend({
            required: {
                onlyIf: function () {
                    return self.imageType() === imageTypes.image
                }
            }
        }),
        instanceTemplate: ko.observable().extend({
            required: {
                onlyIf: function () {
                    return self.imageType() === imageTypes.template
                }
            }
        }),
        zone: ko.observable().extend({required: true}),
        network: ko.observable().extend({
            required: {
                onlyIf: function () {
                    return self.imageType() === imageTypes.image
                }
            }
        }),
        subnet: ko.observable(),
        minInstances: ko.observable(0).extend({required: true, min: 0}),
        maxInstances: ko.observable(1).extend({required: true, min: 0}),
        preemptible: ko.observable(false),
        machineCustom: self.machineCustom,
        machineType: ko.observable().extend({
            validation: {
                validator: function (value) {
                    return value;
                },
                message: "Machine type should not be empty",
                onlyIf: function () {
                    return self.imageType() === imageTypes.image && self.machineCustom() === false;
                }
            }
        }),
        machineCores: ko.observable().extend({
            validation: {
                validator: function (value) {
                    var number = parseInt(value);
                    return number > 0 && (number === 1 || (number % 2 === 0));
                },
                message: "1 or an even number of vCPUs can be created",
                onlyIf: function () {
                    return self.imageType() === imageTypes.image && self.machineCustom() === true;
                }
            }
        }),
        machineMemory: ko.observable().extend({
            validation: {
                validator: function (value) {
                    return value > 900 && (value % 256 === 0);
                },
                message: "Total memory must be a multiple of 256 MB",
                onlyIf: function () {
                    return self.imageType() === imageTypes.image && self.machineCustom() === true;
                }
            }
        }),
        machineMemoryExt: ko.observable(false),
        diskType: ko.observable(),
        vmNamePrefix: ko.observable('').trimmed().extend({required: true, maxLength: maxLength}).extend({
            validation: {
                validator: function (value) {
                    return self.originalImage && self.originalImage['source-id'] === value ||
                        !ko.utils.arrayFirst(self.images(), function (image) {
                            return image['source-id'] === value;
                        });
                },
                message: 'Name prefix should be unique within subscription'
            }
        }).extend({
            pattern: {
                message: 'Name can contain alphanumeric characters, underscore and hyphen',
                params: /^[a-z][a-z0-9_-]*$/i
            }
        }),
        metadata: ko.observable().extend({
            validation: {
                validator: function (value) {
                    if (!value) return true;

                    var root;
                    try {
                        root = JSON.parse(value);
                    } catch (error) {
                        console.log("Unable to parse metadata: " + error);
                        return false;
                    }

                    for (var key in root) {
                        if (root.hasOwnProperty(key)) {
                            if ("object" === typeof(key)) {
                                console.log("Invalid key: " + key);
                                return false;
                            }

                            if ("object" === typeof(root[key])) {
                                console.log("Invalid value for key: " + key);
                                return false;
                            }
                        }
                    }

                    return true;
                },
                message: "Invalid metadata value"
            }
        }),
        growingId: ko.observable(false),
        serviceAccount: ko.observable(),
        scopes: ko.observable(),
        agentPoolId: ko.observable().extend({required: true}),
        profileId: ko.observable()
    });

    // Data from APIs
    self.sourceImages = ko.observableArray([]);
    self.instanceTemplates = ko.observableArray([]);
    self.zones = ko.observableArray([]);
    self.networks = ko.observableArray([]);
    self.subnets = ko.observableArray([]);
    self.machineTypes = ko.observableArray([]);
    self.diskTypes = ko.observableArray([]);
    self.agentPools = ko.observableArray([]);
    self.nets = {};

    // Hidden fields for serialized values
    self.images_data = ko.observable();

    // Deserialized values
    self.images = ko.observableArray();

    // Reload info on credentials change
    self.credentials().accessKey.isValidating.subscribe(function (isValidating) {
        if (isValidating || !self.credentials().accessKey.isValid()) {
            return;
        }

        self.showAccessKey(false);
    });

    self.image().sourceImage.subscribe(function (image) {
        if (!image) return;

        // Fill in vm name prefix
        if (self.image().vmNamePrefix()) return;
        var vmName = image.slice(-maxLength);
        self.image().vmNamePrefix(vmName);
    });

    self.image().zone.subscribe(function (zone) {
        if (!zone) return;
        loadInfoByZone(zone);
    });

    self.image().network.subscribe(function (network) {
        changeSubnets(network, self.image().subnet());
    });

    self.images_data.subscribe(function (data) {
        var images = ko.utils.parseJson(data || "[]");
        images.forEach(function (image) {
            image.imageType = image.imageType || imageTypes.image;
            image.preemptible = getBoolean(image.preemptible);
            image.machineCustom = getBoolean(image.machineCustom);
            image.machineMemoryExt = getBoolean(image.machineMemoryExt);
            image.growingId = getBoolean(image.growingId);
        });
        self.images(images);
    });

    // Dialogs
    self.originalImage = null;

    self.showDialog = function (data) {
        var model = self.image();
        if (!data.hasOwnProperty("minInstances")) {
            data.minInstances = 0
        }
        self.originalImage = data;

        var image = data || {
            imageType: imageTypes.image,
            minInstances: 0,
            maxInstances: 1,
            preemptible: false,
            machineCustom: false,
            growingId: false
        };

        var sourceImage = image.sourceImage;
        if (sourceImage && !ko.utils.arrayFirst(self.sourceImages(), function (item) {
            return item.id === sourceImage;
        })) {
            self.sourceImages({id: sourceImage, text: sourceImage});
        }

        var instanceTemplate = image.instanceTemplate;
        if (instanceTemplate && !ko.utils.arrayFirst(self.instanceTemplates(), function (item) {
            return item.id === instanceTemplate;
        })) {
            self.instanceTemplates({id: instanceTemplate, text: instanceTemplate});
        }

        var machineType = image.machineType;
        if (machineType && !ko.utils.arrayFirst(self.machineTypes(), function (item) {
            return item.id === machineType;
        })) {
            self.machineTypes({id: machineType, text: machineType});
        }

        var diskType = image.diskType;
        if (diskType && !ko.utils.arrayFirst(self.diskTypes(), function (item) {
            return item.id === diskType;
        })) {
            self.diskTypes({id: diskType, text: diskType});
        }

        var network = image.network;
        if (network && !ko.utils.arrayFirst(self.networks(), function (item) {
            return item.id === network;
        })) {
            self.networks({id: network, text: network});
        }

        model.imageType(image.imageType);
        model.sourceImage(image.sourceImage);
        model.instanceTemplate(image.instanceTemplate);
        model.zone(image.zone);
        model.network(network);
        var subnet = image.subnet;
        changeSubnets(network, subnet);
        model.subnet(subnet);
        model.machineCustom(image.machineCustom || false);
        model.machineType(machineType);
        model.machineCores(image.machineCores);
        model.machineMemory(image.machineMemory);
        model.machineMemoryExt(image.machineMemoryExt);
        model.diskType(diskType);
        model.minInstances(image.minInstances);
        model.maxInstances(image.maxInstances);
        model.preemptible(image.preemptible);
        model.vmNamePrefix(image['source-id']);
        model.metadata(image.metadata);
        model.growingId(image.growingId);
        model.serviceAccount(image.serviceAccount);
        model.scopes(image.scopes);
        model.agentPoolId(image.agent_pool_id);
        model.profileId(image.profileId);

        self.showMetadata(false);
        self.showServiceAccount(!!image.serviceAccount);

        self.image.errors.showAllMessages(false);
        dialog.showDialog(!self.originalImage);

        return false;
    };

    self.closeDialog = function () {
        dialog.close();
        return false;
    };

    self.saveImage = function () {
        var model = self.image();
        var image = {
            imageType: model.imageType(),
            sourceImage: model.sourceImage(),
            instanceTemplate: model.instanceTemplate(),
            zone: model.zone(),
            network: model.network(),
            subnet: model.subnet(),
            minInstances: model.minInstances(),
            maxInstances: model.maxInstances(),
            preemptible: model.preemptible(),
            'source-id': model.vmNamePrefix(),
            machineCustom: model.machineCustom(),
            machineType: model.machineType(),
            machineCores: model.machineCores(),
            machineMemory: model.machineMemory(),
            machineMemoryExt: model.machineMemoryExt(),
            diskType: model.diskType(),
            metadata: model.metadata(),
            growingId: model.growingId(),
            serviceAccount: model.serviceAccount(),
            scopes: model.scopes(),
            agent_pool_id: model.agentPoolId(),
            profileId: model.profileId()
        };

        var originalImage = self.originalImage;
        if (originalImage) {
            self.images.replace(originalImage, image);
        } else {
            self.images.push(image);
        }
        saveImages();

        dialog.close();
        return false;
    };

    self.deleteImage = function (image) {
        var message = "Do you really want to delete agent image based on " + image.image + "?";
        var remove = confirm(message);
        if (!remove) {
            return false;
        }

        self.images.remove(image);
        saveImages();

        return false;
    };

    self.loadInfo = function () {
        if (!self.isValidCredentials()) {
            return
        }

        self.loadingResources(true);

        var url = getBasePath() +
            "resource=permissions" +
            "&resource=zones" +
            "&resource=networks" +
            "&resource=images" +
            "&resource=templates";

        $.post(url, getCredentials()).then(function (response) {
            var $response = $(response);
            var errors = getErrors($response);
            if (errors) {
                self.errorResources(errors);
                return;
            } else {
                self.errorResources("");
            }

            self.sourceImages(getSourceImages($response));
            self.instanceTemplates(getInstanceTemplates($response));
            self.zones(getZones($response));
            self.networks(getNetworks($response));
        }, function (error) {
            self.errorResources("Failed to load data: " + error.message);
            console.log(error);
        }).always(function () {
            self.loadingResources(false);
        });
    };

    function changeSubnets(network, subnet) {
        if (!network) return;

        var subnets = self.nets[network];
        if (subnet && !subnets) {
            subnets = [{id: subnet, text: subnet}];
        }

        self.subnets(subnets || []);
    }

    function loadInfoByZone(zoneId) {
        if (!self.isValidCredentials()) {
            return
        }

        var credentialsType = self.credentials().type();
        var accessKey = self.credentials().accessKey();

        var zone = ko.utils.arrayFirst(self.zones(), function (item) {
            return item.id === zoneId;
        });

        self.loadingResourcesByZone(true);

        var url = getBasePath() +
            "zone=" + zone.id +
            "&region=" + zone.region +
            "&resource=subnets" +
            "&resource=machineTypes" +
            "&resource=diskTypes";

        $.post(url, {
            "prop:credentialsType": credentialsType,
            "prop:secure:accessKey": accessKey
        }).then(function (response) {
            var $response = $(response);
            var errors = getErrors($response);
            if (errors) {
                self.errorResources(errors);
                return;
            } else {
                self.errorResources("");
            }

            self.machineTypes(getMachineTypes($response));
            self.diskTypes(getDiskTypes($response));
            getSubnets($response);
            changeSubnets(self.image().network())
        }, function (error) {
            self.errorResources("Failed to load data: " + error.message);
            console.log(error);
        }).always(function () {
            self.loadingResourcesByZone(false);
        });
    }

    self.loadAccessKey = function (file) {
        if (!self.hasFileReader()) return;
        var reader = new FileReader();
        reader.onload = function (e) {
            self.credentials().accessKey(e.target.result);
        };
        reader.readAsText(file);
    };

    self.dragEnterHandler = function () {
        self.isDragOver(true);
    };

    self.dragLeaveHandler = function () {
        self.isDragOver(false);
    };

    self.dropHandler = function (e) {
        self.isDragOver(false);
        var dt = e.dataTransfer || e.originalEvent.dataTransfer;
        if (dt.items) {
            for (var i = 0; i < dt.items.length; i++) {
                if (dt.items[i].kind === "file") {
                    var file = dt.items[i].getAsFile();
                    self.loadAccessKey(file);
                    return
                }
            }
        } else {
            for (var i = 0; i < dt.files.length; i++) {
                self.loadAccessKey(dt.files[i]);
                return
            }
        }
    };

    function saveImages() {
        var images = self.images();
        self.images_data(JSON.stringify(images));
    }

    function getBasePath() {
        return config.baseUrl + "?";
    }

    function getCredentials() {
        var credentialsType = self.credentials().type();
        var accessKey = self.credentials().accessKey();
        if (credentialsType === "key") {
            return {
                "prop:credentialsType": credentialsType,
                "prop:secure:accessKey": accessKey
            };
        } else {
            return {
                "prop:credentialsType": credentialsType
            };
        }
    }

    function getErrors($response) {
        var $errors = $response.find("errors:eq(0) error");
        if ($errors.length) {
            return $.map($errors, function (error) {
                return $(error).text();
            }).join(", ");
        }

        return "";
    }

    function getZones($response) {
        return $response.find("zones:eq(0) zone").map(function () {
            return {id: $(this).attr("id"), region: $(this).attr("region"), text: $(this).text()};
        }).get();
    }

    function getNetworks($response) {
        return $response.find("networks:eq(0) network").map(function () {
            return {id: $(this).attr("id"), text: $(this).text()};
        }).get();
    }

    function getSubnets($response) {
        self.nets = {};
        return $response.find("subnets:eq(0) subnet").map(function () {
            var subnet = {id: $(this).attr("id"), text: $(this).text(), network: $(this).attr("network")};
            var nets = self.nets[subnet.network] || [];
            nets.push(subnet);
            self.nets[subnet.network] = nets;
            return subnet;
        }).get();
    }

    function getMachineTypes($response) {
        return $response.find("machineTypes:eq(0) machineType").map(function () {
            return {id: $(this).attr("id"), text: $(this).text()};
        }).get();
    }

    function getDiskTypes($response) {
        return $response.find("diskTypes:eq(0) diskType").map(function () {
            return {id: $(this).attr("id"), text: $(this).text()};
        }).get();
    }

    function getSourceImages($response) {
        return $response.find("images:eq(0) image").map(function () {
            return {id: $(this).attr("id"), text: $(this).text()};
        }).get();
    }

    function getInstanceTemplates($response) {
        return $response.find("templates:eq(0) template").map(function () {
            return {id: $(this).attr("id"), text: $(this).text()};
        }).get();
    }

    function getBoolean(variable) {
        if (typeof variable === 'string' || variable instanceof String) {
            return ko.utils.parseJson(variable);
        }

        return variable
    }

    (function loadAgentPools() {
        var url = config.baseUrl + "?resource=agentPools&projectId=" + encodeURIComponent(config.projectId);
        return $.post(url).then(function (response) {
            var $response = $(response);
            var errors = getErrors($response);
            if (errors) {
                self.errorResources(errors);
                return;
            } else {
                self.errorResources("");
            }

            var agentPools = $response.find("agentPools:eq(0) agentPool").map(function () {
                return {
                    id: $(this).attr("id"),
                    text: $(this).text()
                };
            }).get();

            self.agentPools(agentPools);
            self.image().agentPoolId.valueHasMutated();
        }, function (error) {
            self.errorResources("Failed to load data: " + error.message);
            console.log(error);
        });
    })();

    self.afterRender = function () {
        if (!self.credentials().accessKey()) {
            self.showAccessKey(true);
        }
    };
}
