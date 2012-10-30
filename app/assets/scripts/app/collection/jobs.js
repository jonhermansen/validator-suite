define(["lib/Logger", "model/job", "collection/collection"], function (Logger, Job, Collection) {

    "use strict";

    var logger = new Logger("Jobs"),
        Jobs;

    Jobs = Collection.extend({

        model: Job

    });

    Jobs.View = Jobs.View.extend({

        templateId: "job-template",

        attributes: {
            id: "jobs"
        },

        sortParams: [
            "name",
            "entrypoint",
            "status",
            "completedOn",
            "errors",
            "warnings",
            "resources",
            "maxResources",
            "health"
        ],

        init: function () {
            var self = this, view, input;
            if (!this.isList()) {
                view = this.collection.at(0).view();
                view.options.assertions = this.options.assertions;
                view.options.resources = this.options.resources;
                view.addSearchHandler();
            }
            $("#actions input[name=search]").bind("keyup change", function () {
                input = this;
                setTimeout(function () {
                    self.search(input.value, input);
                }, 0);
            });
        },

        emptyMessage: function () {
            return "No jobs have been configured yet. <a href='" + this.collection.url + "/new" + "'>Create your first job.</a>";
        }

    });

    return Jobs;

});