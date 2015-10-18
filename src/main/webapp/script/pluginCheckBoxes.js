// send async request to the given URL (which will send back serialized ListBoxModel object),
// then use the result to fill the list box.
function updateCheckBox(container ,url,config) {
    config = config || {};
    var originalPlugins = [];
    var pluginNode;
    if (pluginNode = $('waltiOriginalPlugins')) {
        originalPlugins = pluginNode.value.split(',');
    }
    config.onSuccess = function(rsp) {
        var c = $(container);
        while (container.hasChildNodes()) {
            // clear children
            container.removeChild(container.lastChild);
        }

        var opts = eval('('+rsp.responseText+')').values;

        for( var i=0; i<opts.length; i++ ) {
            var pluginName = opts[i].name;
            var box = new Element('input', {
                type: 'checkbox',
                id: pluginName,
                name: '_.plugins',
                value: pluginName,
                json: pluginName
            });
            if (originalPlugins.indexOf(pluginName) !== -1) {
                box.writeAttribute('checked', 'true');
            }

            var label = new Element('label', { for: pluginName });
            label.insert(pluginName);
            if (pluginName === 'skipfish') {
                var t = new Template('（事前に<a href="#{url}" target="_blank">Walti.ioのターゲット画面</a>から設定を行ってください）');
                label.insert(t.evaluate({url: 'https://app.walti.io/targets/' + config.parameters.target}));
            }

            var div = new Element('div');
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
