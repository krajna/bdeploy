//@ts-check

describe('Central/Managed Basic Test', function () {
  var groupName = 'Demo';
  var instanceName = 'TestInstance';
  var instanceName2 = 'TestInstance2';

  before(() => {
    cy.cleanAllGroups('MANAGED');
    cy.cleanAllGroups('CENTRAL');
  });

  it('Creates a group on the central server', () => {
    cy.visitCentral('/');
    cy.createGroup(groupName, 'CENTRAL');
  });

  it('Attaches a managed server to the central server', () => {
    cy.attachManaged(groupName, true);
  });

  it('Deletes and re-attaches the managed server to the central server', () => {
    cy.deleteGroup(groupName, 'MANAGED');

    cy.visitCentral('/');
    // go to managed servers and sync -> fails
    cy.enterGroup(groupName);
    cy.pressMainNavButton('Managed Servers');
    cy.get('app-servers-browser').should('exist');
    cy.inMainNavContent(() => {
      cy.get('app-bd-server-sync-button').should('have.length', 1).click();
      cy.checkAndConfirmSnackbar('was not found'); // Unfortunately, <url> was not found...
    });
    // back in groups browser -> re-enter managed servers
    cy.get('app-groups-browser').should('exist');
    cy.enterGroup(groupName);
    cy.pressMainNavButton('Managed Servers');
    cy.get('app-servers-browser').should('exist');
    cy.inMainNavContent(() => {
      cy.contains('tr', 'Description of managed server').should('have.length', 1).click();
    });
    cy.inMainNavFlyin('app-server-details', () => {
      cy.get(`app-bd-button[text="Delete"]`).click();

      cy.get('app-bd-dialog-message').within(() => {
        cy.contains('button', 'Yes').should('exist').and('be.enabled').click();
      });
    });

    cy.attachManaged(groupName);
  });

  it('Creates an instance on managed server', () => {
    cy.uploadProductIntoGroup(groupName, 'test-product-1-direct.zip', false, 'MANAGED');

    cy.createInstance(groupName, instanceName, 'Demo Product', '1.0.0', 'MANAGED');

    cy.visitCentral('/');
    cy.waitUntilContentLoaded();
    cy.enterGroup(groupName);
    cy.inMainNavContent(() => {
      cy.contains('tr', instanceName).should('not.exist');
    });

    cy.pressMainNavButton('Managed Servers');
    cy.get('app-servers-browser').should('exist');
    cy.inMainNavContent(() => {
      cy.get('app-bd-server-sync-button').should('have.length', 1).click();
    });

    cy.pressMainNavButton('Instances');
    cy.inMainNavContent(() => {
      cy.contains('tr', instanceName)
        .should('exist')
        .within(() => {
          cy.get('.local-na-chip').should('exist');
        });
    });
  });

  it('Synchronizes product from managed', () => {
    cy.pressMainNavButton('Products');

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Synchronize');
    });

    cy.screenshot('Doc_CentralProdSync');

    cy.inMainNavFlyin('app-product-sync', () => {
      cy.get('button[data-cy^="Download to central"]').click();
    });

    cy.screenshot('Doc_CentralProdSyncServer');

    cy.inMainNavFlyin('app-select-managed-server', () => {
      cy.contains('tr', 'localhost').click();
    });

    cy.screenshot('Doc_CentralProdSyncVersion');

    cy.inMainNavFlyin('app-managed-transfer', () => {
      cy.contains('tr', '1.0.0').within(() => {
        cy.get('input[type="checkbox"]').check({ force: true });
      });

      cy.get('button[data-cy="Transfer"]').should('be.enabled').click();
    });

    cy.inMainNavContent(() => {
      cy.contains('tr', '1.0.0').should('exist');
    });

    cy.pressMainNavButton('Instances');

    cy.inMainNavContent(() => {
      cy.contains('tr', instanceName)
        .should('exist')
        .within(() => {
          cy.get('.local-na-chip').should('not.exist');
        });
    });
  });

  it('Creates an instance on central server', () => {
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip', false, 'CENTRAL');
    cy.createInstance(groupName, instanceName2, 'Demo Product', '2.0.0', 'CENTRAL');

    cy.visitManaged('/');
    cy.waitUntilContentLoaded();
    cy.enterGroup(groupName);
    cy.inMainNavContent(() => {
      cy.contains('tr', instanceName2).should('exist');
    });

    cy.screenshot('Doc_CentralInstanceList');
  });

  it('Configures instance on central server', () => {
    cy.enterInstance(groupName, instanceName2, 'CENTRAL');

    cy.screenshot('Doc_CentralInstanceDashboard');

    cy.pressToolbarButton('Synchronize');
    cy.pressMainNavButton('Instance Configuration');

    cy.waitUntilContentLoaded();

    cy.screenshot('Doc_CentralInstanceConfiguration');

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
        cy.fillFormInput('Text Value', 'Test');
        cy.fillFormInput('Sleep Timeout', '30');

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

  it('Checks product not available on managed', () => {
    cy.visitManaged('/');
    cy.enterGroup(groupName);
    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('tr', instanceName2)
        .should('exist')
        .within(() => {
          cy.get('.local-na-chip').should('exist');
        });
    });
  });

  it('Synchronizes, installs, activates on central', () => {
    cy.enterInstance(groupName, instanceName2, 'CENTRAL');
    cy.pressToolbarButton('Synchronize');

    cy.contains('.bd-rect-card', 'no active version')
      .should('exist')
      .within(() => {
        cy.waitForApi(() => {
          cy.get('button[data-cy="Install"]').should('be.enabled').click();
        });

        cy.waitForApi(() => {
          cy.get('button[data-cy="Activate"]').should('be.enabled').click();
        });
      });

    cy.waitUntilContentLoaded();
    cy.contains('app-instance-server-node', 'master').within(() => {
      cy.contains('tr', 'Server No Sleep').should('exist');
      cy.contains('tr', 'Server With Sleep').should('exist');
    });
  });

  it('Checks product implicitly available on managed', () => {
    cy.visitManaged('/');
    cy.enterGroup(groupName);
    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('tr', instanceName2)
        .should('exist')
        .within(() => {
          cy.get('.local-na-chip').should('not.exist');
        });
    });
  });

  it('Starts process on central', () => {
    cy.enterInstance(groupName, instanceName2, 'CENTRAL');
    cy.pressToolbarButton('Synchronize');
    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('tr', 'Another Server With Sleep').click();
    });

    cy.inMainNavFlyin('app-process-status', () => {
      cy.contains('button', 'play_arrow').click();
      cy.contains('button', 'stop').should('be.enabled');
    });

    cy.inMainNavContent(() => {
      cy.contains('tr', 'Another Server With Sleep').within(() => {
        cy.get('app-process-status-icon[data-cy="RUNNING"]').should('exist');
      });
    });
  });

  it('Stops process on managed', () => {
    cy.enterInstance(groupName, instanceName2, 'MANAGED');
    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('tr', 'Another Server With Sleep')
        .within(() => {
          cy.get('app-process-status-icon[data-cy="RUNNING"]').should('exist');
        })
        .click();
    });

    cy.inMainNavFlyin('app-process-status', () => {
      cy.contains('button', 'stop').should('be.enabled').click();
    });
  });

  it('Deletes the group on central and managed server', () => {
    cy.deleteGroup(groupName, 'CENTRAL');

    cy.visitManaged('/');
    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('tr', groupName).should('exist');
    });

    cy.deleteGroup(groupName, 'MANAGED');
  });
});
