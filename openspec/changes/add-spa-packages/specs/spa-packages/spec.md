## ADDED Requirements

### Requirement: Admin can manage spa packages
The system SHALL allow admin users to create, view, edit, search, hide/show, and delete spa package records.

#### Scenario: Admin creates a spa package
- **WHEN** an admin saves a valid spa package with name, category, price, duration, description, and visibility status
- **THEN** the system stores the package in `spa_packages` with generated id, timestamps, and the provided package fields

#### Scenario: Admin edits a spa package
- **WHEN** an admin updates an existing spa package
- **THEN** the system updates the package document and refreshes the admin package list

#### Scenario: Admin searches spa packages
- **WHEN** an admin enters search text matching a package name or category
- **THEN** the system displays only matching spa packages in the admin management list

#### Scenario: Admin changes package visibility
- **WHEN** an admin toggles a spa package active state
- **THEN** the system updates whether that package can appear in the customer-facing spa catalog

#### Scenario: Admin deletes a spa package
- **WHEN** an admin confirms deletion of a spa package
- **THEN** the system removes the corresponding `spa_packages` document

### Requirement: Admin dashboard exposes spa package management
The system SHALL provide an admin dashboard entry for spa package management and SHALL show basic spa package counts.

#### Scenario: Admin opens spa package management
- **WHEN** an admin taps the spa package management entry from the dashboard
- **THEN** the system opens the spa package management screen

#### Scenario: Dashboard shows package count
- **WHEN** spa package documents exist in Firestore
- **THEN** the admin dashboard displays the total number of spa packages

### Requirement: Customers can browse active spa packages
The system SHALL show customers a spa package catalog containing only active spa packages.

#### Scenario: Customer opens spa catalog
- **WHEN** a customer opens the spa area from the main customer experience
- **THEN** the system displays active spa packages with image, name, category, duration, price, and short description

#### Scenario: Hidden package is not shown
- **WHEN** a spa package has `isActive` set to false
- **THEN** the system excludes that package from the customer-facing spa catalog

#### Scenario: Customer filters spa packages by category
- **WHEN** a customer selects a spa package category
- **THEN** the system displays only active packages in that category

#### Scenario: Customer searches spa packages
- **WHEN** a customer searches by package name or category
- **THEN** the system displays matching active spa packages

### Requirement: Customers can view spa package details
The system SHALL provide a detail view for each active spa package.

#### Scenario: Customer opens package detail
- **WHEN** a customer selects a spa package from the catalog
- **THEN** the system shows package image, name, price, duration, category, description, benefits, steps, and suitability information

#### Scenario: Missing optional detail fields
- **WHEN** a spa package has empty optional lists such as benefits, steps, or suitable audience
- **THEN** the system still displays the package detail screen without crashing

### Requirement: Spa packages prepare for future booking without enabling booking
The system SHALL store duration and service metadata needed by future appointment booking, but this change SHALL NOT create appointments or consultant workflows.

#### Scenario: Package includes scheduling metadata
- **WHEN** an admin creates or edits a spa package
- **THEN** the system stores the package duration in minutes with the package record

#### Scenario: Booking is out of scope
- **WHEN** a customer views a spa package in this change
- **THEN** the system does not create appointment records, assign consultants, or manage appointment status
