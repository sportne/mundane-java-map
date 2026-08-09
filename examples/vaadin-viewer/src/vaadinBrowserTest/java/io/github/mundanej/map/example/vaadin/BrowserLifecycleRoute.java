package io.github.mundanej.map.example.vaadin;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.Route;
import io.github.mundanej.map.vaadin.MundaneMap;

/** Test-lane route that detaches and reattaches one real server-owned map component. */
@Tag("main")
@Route("browser-evidence/lifecycle")
@SuppressWarnings("serial")
public final class BrowserLifecycleRoute extends Component {
    private final MundaneMap map = new MundaneMap();
    private boolean mapAttached = true;

    /** Creates the lifecycle evidence route. */
    public BrowserLifecycleRoute() {
        setId("browser-lifecycle-route");
        map.setId("browser-lifecycle-map");
        map.setWidth("640px");
        map.setHeight("420px");
        Element detach = button("detach-map", "Detach map");
        detach.addEventListener("click", ignored -> detachMap());
        Element attach = button("reattach-map", "Reattach map");
        attach.addEventListener("click", ignored -> attachMap());
        getElement().appendChild(detach, attach, map.getElement());
    }

    @Override
    protected void onDetach(DetachEvent event) {
        map.close();
        super.onDetach(event);
    }

    private void detachMap() {
        if (mapAttached) {
            getElement().removeChild(map.getElement());
            mapAttached = false;
        }
    }

    private void attachMap() {
        if (!mapAttached) {
            getElement().appendChild(map.getElement());
            mapAttached = true;
        }
    }

    private static Element button(String id, String text) {
        Element button = new Element("button");
        button.setAttribute("id", id);
        button.setText(text);
        return button;
    }
}
