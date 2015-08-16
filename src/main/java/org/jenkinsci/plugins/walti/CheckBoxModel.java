package org.jenkinsci.plugins.walti;

import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Flavor;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

@ExportedBean
public class CheckBoxModel extends ArrayList<CheckBoxModel.Item> implements HttpResponse {

    @ExportedBean(defaultVisibility=999)
    public static final class Item {
        @Exported
        public String name;

        @Exported
        public String displayName;

        @Exported
        public boolean checked;

        public Item(String displayName) {
            this(displayName, displayName, false);
        }

        public Item(String displayName, String name) {
            this(displayName, name, false);
        }

        public Item(String displayName, String name, boolean checked) {
            this.displayName = displayName;
            this.name = name;
            this.checked = checked;
        }
    }

    public CheckBoxModel() {
    }

    public CheckBoxModel(Collection<Item> c) {
        super(c);
    }

    public CheckBoxModel(Item... data) {
        super(Arrays.asList(data));
    }

    public void writeTo(StaplerRequest req,StaplerResponse rsp) throws IOException, ServletException {
        rsp.serveExposedBean(req, this, Flavor.JSON);
    }

    public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
        writeTo(req,rsp);
    }

    /**
     * @deprecated
     *      Exposed for stapler. Not meant for programatic consumption.
     */
    @Exported
    public Item[] values() {
        return toArray(new Item[size()]);
    }
}