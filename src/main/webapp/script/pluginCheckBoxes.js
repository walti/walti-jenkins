// send async request to the given URL (which will send back serialized ListBoxModel object),
// then use the result to fill the list box.
function updateCheckBox(container ,url,config) {
    config = config || {};
    config = object(config);
    var originalPlugins = [];
    var pluginNode;
    if (pluginNode = $('waltiOriginalPlugins')) {
        originalPlugins = pluginNode.value.split(',');
    }
    var originalOnSuccess = config.onSuccess;
    config.onSuccess = function(rsp) {
        var c = $(container);
        while (container.hasChildNodes()) {
            // clear children
            container.removeChild(container.lastChild);
        }

        var opts = eval('('+rsp.responseText+')').values;

        for( var i=0; i<opts.length; i++ ) {
            var box = document.createElement('input');
            box.setAttribute('type', 'checkbox');
            box.setAttribute('id', opts[i].name);
            box.setAttribute('name', '_.plugins');
            box.setAttribute('value', opts[i].name);
            box.setAttribute('json', opts[i].name);
            if (originalPlugins.indexOf(opts[i].name) !== -1) {
                box.setAttribute('checked', 'true');
            }

            var label = document.createElement('label');
            label.htmlFor = opts[i].name;
            label.appendChild(document.createTextNode(opts[i].name));

            var div = document.createElement('div');
            div.appendChild(box);
            div.appendChild(label);
            container.appendChild(div);
        }
        if (pluginNode) {
            pluginNode.parentNode.removeChild(pluginNode);
        }
    },
    config.onFailure = function(rsp) {

    }

    new Ajax.Request(url, config);
}


Behaviour.specify("#pluginCheckboxes", 'plugins', 1000, function(e) {
        refillOnChange(e,function(params) {
            var value = e.value;
            updateCheckBox(e, e.getAttribute("fillUrl"), {
                parameters: params
            });
        });
});
