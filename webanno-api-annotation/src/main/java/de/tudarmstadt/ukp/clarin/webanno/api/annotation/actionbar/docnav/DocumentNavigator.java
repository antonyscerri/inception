/*
 * Copyright 2019
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.api.annotation.actionbar.docnav;

import static de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaBehavior.visibleWhen;
import static wicket.contrib.input.events.EventType.click;
import static wicket.contrib.input.events.key.KeyType.Page_down;
import static wicket.contrib.input.events.key.KeyType.Page_up;
import static wicket.contrib.input.events.key.KeyType.Shift;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.feedback.IFeedback;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.spring.injection.annot.SpringBean;

import de.tudarmstadt.ukp.clarin.webanno.api.ProjectService;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.actionbar.export.ExportDocumentDialog;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.actionbar.open.OpenDocumentDialog;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.page.AnnotationPageBase;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaAjaxLink;
import wicket.contrib.input.events.InputBehavior;
import wicket.contrib.input.events.key.KeyType;

public class DocumentNavigator
    extends Panel
{
    private static final long serialVersionUID = 7061696472939390003L;

    private @SpringBean ProjectService projectService;

    private AnnotationPageBase page;

    private final ExportDocumentDialog exportDialog;

    public DocumentNavigator(String aId, AnnotationPageBase aPage)
    {
        super(aId);

        page = aPage;

        add(new LambdaAjaxLink("showPreviousDocument", t -> actionShowPreviousDocument(t))
                .add(new InputBehavior(new KeyType[] { Shift, Page_up }, click)));

        add(new LambdaAjaxLink("showNextDocument", t -> actionShowNextDocument(t))
                .add(new InputBehavior(new KeyType[] { Shift, Page_down }, click)));

        add(new LambdaAjaxLink("showOpenDocumentDialog", this::actionShowOpenDocumentDialog));

        add(exportDialog = new ExportDocumentDialog("exportDialog", page.getModel()));
        add(new LambdaAjaxLink("showExportDialog", exportDialog::show).add(visibleWhen(() -> {
            AnnotatorState state = page.getModelObject();
            return state.getProject() != null
                    && (projectService.isManager(state.getProject(), state.getUser())
                            || !state.getProject().isDisableExport());
        })));
    }

    /**
     * Show the previous document, if exist
     */
    public void actionShowPreviousDocument(AjaxRequestTarget aTarget)
    {
        boolean documentChanged = page.getModelObject()
                .moveToPreviousDocument(page.getListOfDocs());
        if (!documentChanged) {
            info("There is no previous document");
            aTarget.addChildren(getPage(), IFeedback.class);
            return;
        }
        page.actionLoadDocument(aTarget);
    }

    /**
     * Show the next document if exist
     */
    public void actionShowNextDocument(AjaxRequestTarget aTarget)
    {
        boolean documentChanged = page.getModelObject().moveToNextDocument(page.getListOfDocs());
        if (!documentChanged) {
            info("There is no next document");
            aTarget.addChildren(getPage(), IFeedback.class);
            return;
        }
        page.actionLoadDocument(aTarget);
    }

    public void actionShowOpenDocumentDialog(AjaxRequestTarget aTarget)
    {
        page.getModelObject().getSelection().clear();
        page.getFooterItems().getObject().stream()
                .filter(component -> component instanceof OpenDocumentDialog)
                .map(component -> (OpenDocumentDialog) component).findFirst()
                .ifPresent(dialog -> dialog.show(aTarget));
    }
}
