package io.github.mundanej.map.vaadin;

import java.util.List;

/** Layer capability exposing only server-selected, non-executable label inputs. */
interface BrowserLabelLayer {
    List<BrowserLabelCandidate> browserLabelCandidates();
}
