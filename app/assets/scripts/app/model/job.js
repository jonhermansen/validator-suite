define(["lib/Logger", "lib/Util", "libs/backbone", "model/model"], function (Logger, Util, Backbone, Model) {

    "use strict";

    var logger = new Logger("Job"),
        Job;

    Job = Model.extend({

        defaults: {
            name: "New Job",
            entrypoint: "http://www.example.com",
            status: "idle",
            completedOn: null, // .timestamp, .legend1, .legend2
            warnings: 0,
            errors: 0,
            resources: 0,
            maxResources: 0,
            health: -1
        },

        methodMap: {
            'run':    'POST',
            'stop':   'POST',
            'create': 'POST',
            'read':   'GET',
            'update': 'POST',
            'delete': 'POST'
        },

        search: function (search) {
            return this.get("name").toLowerCase().indexOf(search.toLowerCase()) > -1 ||
                    this.get("entrypoint").toLowerCase().indexOf(search.toLowerCase()) > -1;
        },

        reportUrl: function () {
            return this.url() + "/resources";
        },

        isIdle: function () {
            return this.get("status") === "idle";
        },

        isCompleted: function () {
            return this.get("completedOn") !== null;
        },

        run: function (options) {
            logger.info(this.get("name") + ": run");
            var action = this._serverEvent('run', options);
            logger.debug(action);
        },

        stop: function (options) { this._serverEvent('stop', options); },

        validate: function (attrs) {
            if (!attrs.name || attrs.name.length < 1) {
                logger.warn("Name required");
                return "Name required";
            }
            if (!attrs.entrypoint || attrs.entrypoint.length < 1) {
                logger.warn("Start URL required");
                return "Start URL required";
            }
            if (!attrs.maxResources || attrs.maxResources < 1 || attrs.maxResources > 500) {
                logger.warn("Max resources must be between 1 and 500");
                return "Max resources must be between 1 and 500";
            }
        },

        _serverEvent: function (event, options) {
            var model, success, trigger, xhr;

            if (this.isNew()) { throw new Error("can't run actions on a new job"); }

            options = options ? _.clone(options) : {};
            model = this;
            success = options.success;
            trigger = function () {
                model.trigger(event, model, model.collection, options);
            };

            options.success = function (resp) {
                if (options.wait) { trigger(); }
                if (success) {
                    success(model, resp);
                } else {
                    model.trigger('sync', model, resp, options);
                }
            };
            options.error = Backbone.wrapError(options.error, model, options);
            xhr = (this.sync || Backbone.sync).call(this, event, this, options);
            if (!options.wait) { trigger(); }
            return xhr;
        },

        sync: function (method, model, options) {
            var type, params;
            type = this.methodMap[method];
            params = { type: type };
            if (!options.url) {
                params.url = model.url() || Util.exception("A 'url' property or function must be specified");
            }
            if (method !== 'read') {
                params.data = { action: method };
            }
            if (!options.data && model && (method === 'create' || method === 'update')) {
                params.data = _.extend(
                    params.data,
                    {
                        id: model.id,
                        name: model.attributes.name, //; model.attributes
                        entrypoint: model.attributes.entrypoint,
                        maxResources: model.attributes.maxResources,
                        assertor: model.attributes.assertor
                    }
                );
            }

            logger.info("Sending request: ");
            logger.debug(_.extend(params, options));

            return $.ajax(_.extend(params, options));
        }

    });

    Job.View = Job.View.extend({

        templateId: "job-template",

        events: {
            "click .stop"   : "stop",
            "click .run"    : "run",
            "click .delete" : "_delete",
            "change [name=group]" : "group"
            //"keyup [name=search]": "search"
        },

        group: function (event) {
            $(event.target).parents('form').submit();
        },

        init: function () {
            this.el.setAttribute("data-id", this.model.id);
        },

        templateOptions: function () {
            return {
                url : this.model.url(),
                isIdle: this.model.isIdle(),
                reportUrl : this.model.reportUrl(),
                isCompleted: this.model.get("completedOn") !== null,
                hasResources: function () { return $("#resources").size() > 0; }
            };
        },

        stop: function () {
            this.model.stop({ wait: true });
            return false;
        },

        run: function () {
            this.model.run({ wait: true });
            return false;
        },

        addSearchHandler: function () {
            var collec = this.options.resources || this.options.assertions;
            if (!collec) { return; }
            this.$(".actions input[name=search]").bind("keyup change", function () {
                var value = this.value;
                setTimeout(function () {
                    collec.view().search(value);
                }, 0);
            });
        },

        afterRender: function () {
            this.addSearchHandler();
        }

    });

    Job.fromHtml = function (value, $elem) {
        return {
            id: $elem.attr("data-id"),
            name: value('data-name'),
            entrypoint: value('data-entrypoint'),
            status: value('data-status'),
            completedOn: _.isUndefined(value('data-completed')) ? null : {
                timestamp: value('data-completed'),
                legend1: value('data-completed-legend1'),
                legend2: value('data-completed-legend2')
            },
            warnings: parseInt(value('data-warnings'), 10),
            errors: parseInt(value('data-errors'), 10),
            resources: parseInt(value('data-resources'), 10),
            maxResources: parseInt(value('data-maxResources'), 10),
            health: parseInt(value('data-health'), 10)
        };
    };

    return Job;

});