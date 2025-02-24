//@ts-check

const { validateZip } = require('../support/utils');

describe('Groups Tests (Clients)', () => {
  var groupName = 'Demo';
  var instanceName = 'TestInstance';

  before(() => {
    cy.cleanAllGroups();
    cy.authenticatedRequest({ method: 'DELETE', url: `${Cypress.env('backendBaseUrl')}/auth/admin?name=client`, failOnStatusCode: false });
  });

  beforeEach(() => {
    cy.login();
  });

  it('Creates a group', () => {
    cy.visit('/');
    cy.createGroup(groupName);
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip');
    cy.createInstance(groupName, instanceName, 'Demo Product', '2.0.0');
  });

  it('Prepares the instance', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Configuration');

    cy.waitUntilContentLoaded();

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
        cy.fillFormSelect('Client Apps', 'Apply to Client Applications');

        cy.get('button[data-cy="Confirm"]').click();
      });

      cy.contains('app-bd-notification-card', 'Assign Variable Values').within(() => {
        cy.fillFormInput('Text Value', 'Test');
        cy.fillFormInput('Sleep Timeout', '5');

        cy.get('button[data-cy="Confirm"]').click();
      });
    });

    cy.inMainNavContent(() => {
      cy.contains('app-config-node', 'Client Applications').within(() => {
        cy.get('tr:contains("Client Test")').should('have.length', 2);
      });

      cy.pressToolbarButton('Save');
    });

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('.bd-rect-card', 'has no active version')
        .should('exist')
        .within(() => {
          cy.waitForApi(() => {
            cy.get('button[data-cy="Install"]').should('be.enabled').click();
          });

          cy.waitForApi(() => {
            cy.get('button[data-cy="Activate"]').should('be.enabled').click();
          });
        });
    });
  });

  it('Tests client applications page', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Client Applications');

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('tr', instanceName).should('exist');
      cy.get('tr:contains("Client Test")').should('have.length', 1).click(); // only one shown due to OS!
    });

    cy.waitUntilContentLoaded();
    cy.screenshot('Doc_ClientApps');

    // current OS
    cy.inMainNavFlyin('app-client-detail', () => {
      cy.get('button[data-cy="Download Installer"]').should('be.enabled').downloadByLocationAssign('test-installer.bin');
      cy.get('button[data-cy^="Click"]').should('be.enabled').downloadByLinkClick('test-click-start.json');
      cy.readFile(Cypress.config('downloadsFolder') + '/' + 'test-click-start.json')
        .its('groupId')
        .should('eq', groupName);

      cy.get('button[data-cy="Download Launcher Installer"]').should('be.enabled').downloadByLocationAssign('test-launcher-installer.bin');
      // intentionally NOT downloading launcher as it is quite huge and downloading is slow even locally.
    });

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Data Grouping');
    });

    // remove the second (OS) grouping level.
    cy.contains('mat-card', 'Grouping Level').within(() => {
      cy.get('button[data-cy="Remove"]').click();
    });

    cy.get('.cdk-overlay-backdrop-showing').click('top');

    cy.get('tr:contains("Client Test")').should('have.length', 2);
  });

  it('Creates a local user', () => {
    cy.authenticatedRequest({ method: 'PUT', url: `${Cypress.env('backendBaseUrl')}/auth/admin/local`, body: { name: 'client', password: 'client' } });
  });

  it('Tests no group visible', () => {
    cy.pressMainNavTopButton('User Settings');
    cy.inMainNavFlyin('app-settings', () => {
      cy.get('button[data-cy="Logout"]').click();
    });
    cy.waitUntilContentLoaded();
    cy.fillFormInput('user', 'client');
    cy.fillFormInput('pass', 'client');

    cy.get('button[type="submit"]').click();

    cy.inMainNavContent(() => {
      cy.contains('Welcome to BDeploy').should('exist');
    });
  });

  it('Assigns Client permissions', () => {
    cy.visit('/');
    cy.enterGroup(groupName);

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Group Settings');
    });

    cy.inMainNavFlyin('app-settings', () => {
      cy.get('button[data-cy^="Instance Group Permissions"]').click();
      cy.contains('tr', 'client')
        .should('exist')
        .within(() => {
          cy.get('button[data-cy^="Modify permissions"]').click();
        });

      cy.intercept({ method: 'GET', url: `/api/group/${groupName}/users` }).as('getUsers');

      cy.waitForApi(() => {
        cy.contains('app-bd-notification-card', 'Modify').within(() => {
          cy.fillFormSelect('modPerm', 'CLIENT');
          cy.get('button[data-cy="OK"]').click();
        });
      });

      cy.wait('@getUsers');
      cy.waitUntilContentLoaded();

      cy.contains('tr', 'client').within(() => {
        cy.contains('.local-CLIENT-chip', 'CLIENT').should('exist');
      });
    });
  });

  it('Tests clients visible', () => {
    cy.pressMainNavTopButton('User Settings');
    cy.inMainNavFlyin('app-settings', () => {
      cy.get('button[data-cy="Logout"]').click();
    });
    cy.waitUntilContentLoaded();
    cy.fillFormInput('user', 'client');
    cy.fillFormInput('pass', 'client');

    cy.get('button[type="submit"]').click();

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('Welcome to BDeploy').should('not.exist');
    });

    cy.inMainNavContent(() => {
      cy.contains('tr', groupName).should('exist').click();
      cy.contains('mat-toolbar', 'Client Applications').should('exist');
    });
    cy.pressMainNavButton('Client Applications');

    cy.inMainNavContent(() => {
      cy.contains('tr', instanceName).should('exist');
      cy.get('tr:contains("Client Test")').should('have.length', 1); // only one shown due to OS!
    });
  });

  it('Deletes the group', () => {
    cy.deleteGroup(groupName);
    cy.authenticatedRequest({ method: 'DELETE', url: `${Cypress.env('backendBaseUrl')}/auth/admin?name=client` });
  });
});
