/*
 * Copyright 2020
 * Ubiquitous Knowledge Processing (UKP) Lab
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


package de.tudarmstadt.ukp.inception.app.ui.monitoring.page;

import static de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst.PAGE_PARAM_PROJECT_ID;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.persistence.NoResultException;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxEventBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DefaultDataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.LambdaColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.PropertyColumn;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.markup.html.panel.EmptyPanel;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.apache.wicket.request.resource.ResourceReference;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.util.string.StringValue;
import org.wicketstuff.annotation.mount.MountPath;

import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.api.ProjectService;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.security.model.User;
import de.tudarmstadt.ukp.clarin.webanno.support.bootstrap.BootstrapFeedbackPanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.core.menu.MenuItemRegistry;
import de.tudarmstadt.ukp.clarin.webanno.ui.core.page.ApplicationPageBase;
import de.tudarmstadt.ukp.inception.app.ui.monitoring.support.DataProvider;
import de.tudarmstadt.ukp.inception.app.ui.monitoring.support.ImagePanel;
import de.tudarmstadt.ukp.inception.app.ui.monitoring.support.ModalPanel;

@MountPath("workload.html")
public class MonitoringPage extends ApplicationPageBase
{

    //Feedbackpanel
    BootstrapFeedbackPanel feedbackPanel;

    //Layout Panel
    private Panel table;

    //Modal Window (popup panel on clicking the image in the metadata column)
    private ModalWindow modalWindow;

    //Default annotations label and textbox
    private NumberTextField DEFAULT_DOCUMENTS_NUMBER_TEXT_FIELD;

    //The Icons
    private static final ResourceReference META =
        new PackageResourceReference(MonitoringPage.class, "book_open.png");

    //Current Project
    private Project currentProject;

    //List containing all documents
    private List<SourceDocument> documentList;

    //List containing all documents finished in their
    // progress of annotation by any user in the project
    private List<String> annotatedDocuments;


    //Default annotations
    public int defaultAnnotations;


    //SpringBeans
    private @SpringBean UserDao userRepository;
    private @SpringBean ProjectService projectService;
    private @SpringBean MenuItemRegistry menuItemService;
    private @SpringBean DocumentService documentService;

    //Default constructor, no project selected (only when workload.html
    // put directly in the browser without any parameters)
    public MonitoringPage() {
        super();


        feedbackPanel = new BootstrapFeedbackPanel("feedbackPanel");
        feedbackPanel.setOutputMarkupId(true);

        //Required due to wicket
        add(new ModalWindow("modalWindow"));
        add(new NumberTextField("defaultDocumentsNumberTextField"));
        add(new EmptyPanel("dataTable"));

        //Error, user is returned to home page, nothing else to do
        //TODO show errors properly
        feedbackPanel.error("No Project selected, please enter the monitoring page only with a valid project reference");
        setResponsePage(getApplication().getHomePage());

    }


    //Constructor with a project
    public MonitoringPage(final PageParameters aPageParameters) {
        super(aPageParameters);
        //Get current Project
        StringValue projectParameter = aPageParameters.get(PAGE_PARAM_PROJECT_ID);
        if (getProjectFromParameters(projectParameter).isPresent()) {
            currentProject = getProjectFromParameters(projectParameter).get();
        } else {
            currentProject = null;
        }



        //Get the current user
        User user = userRepository.getCurrentUser();
        //Check if Project exists
        if (currentProject != null) {

            feedbackPanel = new BootstrapFeedbackPanel("feedbackPanel");
            feedbackPanel.setOutputMarkupId(true);


            //Check if user is allowed to see the monitoring page of this project
            if (currentProject != null && !(projectService.isCurator(currentProject, user)
                || projectService.isManager(currentProject, user)))
            {
                //Required even in case of error due to wicket
                add(new ModalWindow("modalWindow"));
                add(new NumberTextField("defaultDocumentsNumberTextField"));
                add(new EmptyPanel("dataTable"));
                feedbackPanel.error("You have no permission to access project [" + currentProject.getId() + "]");
                setResponsePage(getApplication().getHomePage());

            } else {
                initialize();
            }

        }

        else {
            //Required even in case of error due to wicket
            add(new ModalWindow("modalWindow"));
            add(new NumberTextField("defaultDocumentsNumberTextField"));
            add(new EmptyPanel("dataTable"));
            //Project does not exists, returning to homepage
            error("Project [" + projectParameter + "] does not exist");
            setResponsePage(getApplication().getHomePage());

        }

    }

    public void initialize()
    {
        //get Documents for current project
        documentList = documentService.
            listSourceDocuments(currentProject);

        //Create list with data for the provider
        List<SourceDocument> data = new ArrayList<>();
        for (int i = 0; i < documentList.size(); i++)
        {
            data.add(documentList.get(i));
        }

        //Initialize list for all Finished documents
        annotatedDocuments = new ArrayList<>();


        //List for annotated documents
        for (int i = 0; i < documentService.listFinishedAnnotationDocuments
            (currentProject).size(); i++)
        {
            annotatedDocuments.add(documentService.
                listFinishedAnnotationDocuments(currentProject).get(i).getName());
        }


        //Headers of the table
        List<String> headers = new ArrayList<String>();
        headers.add(getString("Document"));
        headers.add(getString("Finished"));
        headers.add(getString("InProgress"));
        headers.add(getString("Metadata"));

        //Data Provider for the table
        DataProvider dataProvider = new DataProvider(data, headers);

        //Init defaultDocumentsNumberTextField
        DEFAULT_DOCUMENTS_NUMBER_TEXT_FIELD = new
            NumberTextField("defaultDocumentsNumberTextField");

        //Create the modalWindow
        modalWindow = new ModalWindow("modalWindow");
        modalWindow.setTitle("Metadata:");
        add(modalWindow);

        //Columns of the table
        List<IColumn> columns = new ArrayList<>();

        //Each column creates TableMetaData
        columns.add(new LambdaColumn<>(new ResourceModel(getString("Document"))
            , getString("Finished"), SourceDocument::getName));
        columns.add(new LambdaColumn<>(new ResourceModel(getString("Finished"))
            , getString("Finished"), this::getFinishedAmountForDocument));
        columns.add(new LambdaColumn<>(new ResourceModel(getString("InProgress"))
            , getString("InProgress"), this::getInProgressAmountForDocument));

        //own column type, contains only a clickable image (AJAX event)
        // , creates a small panel dialog containing metadata
        columns.add(new PropertyColumn(new Model(getString("Metadata")),
            getString("Metadata")) {
            private static final long serialVersionUID = 1L;
            @Override
            public void populateItem(Item aItem, String aID, IModel aModel) {

                //Add the Icon
                ImagePanel icon = new ImagePanel(aID, META);
                icon.add(new AttributeAppender("style", "cursor: pointer", ";"));
                aItem.add(icon);
                aItem.add(AttributeModifier.append("class", "centering"));

                //Click event in the metadata column
                aItem.add(new AjaxEventBehavior("click") {
                    @Override
                    protected void onEvent(AjaxRequestTarget aTarget) {

                        //Get the current selected Row
                        Item rowItem = aItem.findParent( Item.class );
                        int rowIndex = rowItem.getIndex();

                        //Set contents of the modalWindow
                        modalWindow.setContent(new ModalPanel(modalWindow.
                            getContentId(), documentList.get(rowIndex)));

                        //Open the dialog
                        modalWindow.show(aTarget);
                    }
                });
            }
        });




        //The DefaultDataTable
        table = new DefaultDataTable("dataTable", columns, dataProvider, 20);

        //Miscellaneous for fields
        //DEFAULT_DOCUMENTS_NUMBER_TEXT_FIELD.setRequired(true);

        //Add to page according to the following structure

        //Then default annotations
        add(DEFAULT_DOCUMENTS_NUMBER_TEXT_FIELD);

        //Finally the table
        add(table);

    }


    //Return current project, required for several purposes,
    // might be put globally to be accessible for all packages
    private Optional<Project> getProjectFromParameters(StringValue projectParam)
    {
        if (projectParam == null || projectParam.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(projectService.getProject(projectParam.toLong()));
        }
        catch (NoResultException e) {
            return Optional.empty();
        }
    }



    //Helper method, returns for a document how often it is finished within the project
    public int getFinishedAmountForDocument(SourceDocument aDocument)
    {
        int amount = 0;
        for (int i = 0; i < annotatedDocuments.size(); i++)
        {
            if (aDocument.getName().equals(annotatedDocuments.get(i)))
            {
                amount++;
            }
        }
        return amount;
    }

    //Helper methods, returns for a document how often it is
    // currently in progress within the project
    public int getInProgressAmountForDocument(SourceDocument aDocument)
    {
        int amount = 0;
        int amountUser = projectService.listProjectUsersWithPermissions
            (currentProject).size();

        for (int i = 0; i <  annotatedDocuments.size(); i++)
        {
            if (annotatedDocuments.get(i).equals(aDocument.getName()))
            {
                amount++;
            }
        }

        return amountUser - amount;
    }
}
