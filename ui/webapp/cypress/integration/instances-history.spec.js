//@ts-check

describe('Instance History Tests', () => {
  var groupName = 'Demo';
  var instanceName = 'TestInstance';

  before(() => {
    cy.cleanAllGroups();
  });

  beforeEach(() => {
    cy.login();
  });

  it('Prepares the test (group, products, instance)', () => {
    cy.visit('/');
    cy.createGroup(groupName);
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip');
    cy.createInstance(groupName, instanceName, 'Demo Product', '2.0.0');
  });

  it('Prepares Instance Version', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Configuration');

    cy.waitUntilContentLoaded();

    // create some from a template
    cy.inMainNavContent(() => {
      cy.contains('.bd-rect-card', 'The instance is currently empty').within(() => {
        cy.get('button[data-cy^="Apply Instance Template"]').click();
      });
    });

    cy.waitUntilContentLoaded();

    cy.inMainNavFlyin('app-instance-templates', () => {
      cy.contains('tr', 'Default Configuration')
        .should('exist')
        .within(() => {
          cy.get('button').click();
        });

      cy.contains('app-bd-notification-card', 'Assign Template').within(() => {
        cy.fillFormSelect('Server Apps', 'Apply to master');
        cy.fillFormSelect('Client Apps', 'Apply to Client Applications');

        cy.get('button[data-cy="Confirm"]').click();
      });

      cy.contains('app-bd-notification-card', 'Assign Variable Values').within(() => {
        cy.fillFormInput('Text Value', 'Test Text');
        cy.fillFormInput('Sleep Timeout', '5');

        cy.get('button[data-cy="Confirm"]').click();
      });
    });

    cy.inMainNavContent(() => {
      cy.waitForApi(() => {
        cy.pressToolbarButton('Save');
      });

      cy.waitUntilContentLoaded();
    });
  });

  it('Test Creation History', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance History');

    cy.waitUntilContentLoaded();

    cy.screenshot('Doc_History');

    cy.inMainNavContent(() => {
      cy.get('tr:contains("Created")').should('have.length', 2);
      cy.contains('tr', 'Version 1: Created').click();
    });

    cy.inMainNavFlyin('app-history-entry', () => {
      cy.get('button[data-cy="Show Details"]').click();
    });

    cy.inMainNavFlyin('app-history-view', () => {
      cy.contains('app-history-header-config', 'TestInstance').should('exist');
      cy.pressToolbarButton('Back to Overview');
    });

    cy.screenshot('Doc_HistoryEntry');

    cy.inMainNavFlyin('app-history-entry', () => {
      cy.get('button[data-cy="Compare with Current"]').click();
    });

    cy.inMainNavFlyin('app-history-compare', () => {
      cy.get('app-history-header-config:contains("TestInstance")').should('have.length', 2);
      cy.contains('app-history-diff-field', 'Server No Sleep').should('exist');
      cy.contains('app-history-diff-field', 'Server With Sleep').should('exist');
      cy.contains('app-history-diff-field', 'Client Test Text').should('exist');
    });
    cy.screenshot('Doc_HistoryCompare');
    cy.inMainNavFlyin('app-history-compare', () => {
      cy.pressToolbarButton('Back to Overview');
    });

    cy.inMainNavFlyin('app-history-entry', () => {
      cy.get('button[data-cy="Compare with Active"]').should('be.disabled');
    });
  });

  it('Tests install and activate', () => {
    cy.inMainNavContent(() => {
      cy.contains('tr', 'Version 2: Created').click();
    });

    cy.inMainNavFlyin('app-history-entry', () => {
      cy.get('button[data-cy="Activate"]').should('be.disabled');

      cy.get('button[data-cy="Install"]').click();
      cy.waitUntilContentLoaded();

      cy.get('button[data-cy="Activate"]').should('be.enabled').click();
      cy.waitUntilContentLoaded();
    });

    cy.inMainNavContent(() => {
      cy.contains('tr', 'Version 2: Created').within(() => {
        cy.contains('2 - Active').should('exist');
      });

      cy.pressToolbarButton('Show Deployment Events');

      cy.contains('tr', 'Version 2: Installed').should('exist');
      cy.contains('tr', 'Version 2: Activated').should('exist');
    });

    cy.screenshot('Doc_HistoryDeployment');
  });

  it('Starts and stops a process', () => {
    cy.pressMainNavButton('Instance Dashboard');

    cy.inMainNavContent(() => {
      cy.contains('tr', 'Another Server With Sleep').click();
    });

    cy.inMainNavFlyin('app-process-status', () => {
      cy.contains('button', 'play_arrow').click();

      // wait for crash
      cy.contains('Restart In').should('exist');

      cy.contains('button', 'stop').should('be.enabled').click();
    });
  });

  it('Tests runtime history', () => {
    cy.pressMainNavButton('Instance History');
    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Show Runtime Events');

      cy.contains('tr', 'Another Server With Sleep started')
        .should('exist')
        .within(() => {
          cy.contains('[admin]').should('exist');
        });

      cy.contains('tr', 'Another Server With Sleep crashed')
        .should('exist')
        .within(() => {
          cy.contains('BDeploy System').should('exist');
        });

      cy.contains('tr', 'Another Server With Sleep stopped').should('exist');
    });

    cy.screenshot('Doc_HistoryRuntime');
  });

  it('Cleans up', () => {
    cy.deleteGroup(groupName);
  });
});
