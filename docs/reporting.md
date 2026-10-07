# Reporting

## Overview

Reporting is an important way to keep platform content healthy and trading safe. Users report items, users or behaviour they suspect of breaking the rules, and platform administrators review and act on the reports. The feature builds community oversight so everyone helps keep the marketplace trustworthy.

### Core features
- **Submit a report**: on the product page or other relevant pages, the report button opens a modal where the user submits a report.
- **Choose a report type**: the user picks a preset reason such as "Prohibited item", "Fraud" or "False information".
- **Add a description**: the user can add text to give administrators more context.
- **Administrator review**: administrators have a dedicated back-office page listing every pending report.
- **Handle a report**: administrators review the report and act on it, for example by delisting the item or banning the user, then mark the report "Resolved" or "Invalid".

## Database schema

### report table
```sql
CREATE TABLE `report` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'Report ID',
  `reporter_id` int NOT NULL COMMENT 'Reporter ID',
  `reported_user_id` int DEFAULT NULL COMMENT 'Reported user ID',
  `reported_product_id` int DEFAULT NULL COMMENT 'Reported product ID',
  `report_type` varchar(100) NOT NULL COMMENT 'Report type',
  `description` text COMMENT 'Report description',
  `status` varchar(20) DEFAULT 'PENDING' COMMENT 'Status (PENDING, RESOLVED, INVALID)',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `reporter_id` (`reporter_id`),
  KEY `reported_user_id` (`reported_user_id`),
  KEY `reported_product_id` (`reported_product_id`),
  CONSTRAINT `report_ibfk_1` FOREIGN KEY (`reporter_id`) REFERENCES `user` (`id`),
  CONSTRAINT `report_ibfk_2` FOREIGN KEY (`reported_user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `report_ibfk_3` FOREIGN KEY (`reported_product_id`) REFERENCES `product` (`id`)
);
```

## Backend API

### User endpoints

#### 1. Create a report
`POST /api/reports`
- **Authorization**: `Bearer {token}`
- **Request Body**: `{ "reportedProductId": 123, "reportType": "Prohibited item", "description": "..." }`
- **Response**: a success or failure message.

### Administrator endpoints

#### 1. List all reports
`GET /api/admin/reports`
- **Authorization**: `Bearer {admin-token}`
- **Query Params**: `status=PENDING` (optional filter)
- **Response**: the list of reports with details of the reporter and the reported item or user.

#### 2. Update a report's status
`PUT /api/admin/reports/{reportId}/status`
- **Authorization**: `Bearer {admin-token}`
- **Request Body**: `{ "status": "RESOLVED" }`
- **Response**: a success or failure message.

## Frontend views and components

- **ReportModal.vue**: the report modal component.
  - A reusable component that can be opened anywhere reporting is needed.
  - Contains a form where the user chooses a report type and writes a description.
  - Clicking "Submit" calls `POST /api/reports`.

- **AdminReports.vue**: the administrator's report management page.
  - Route: `/admin/reports`
  - Features:
    - Lists every report in a table or list.
    - Filters by status such as "Pending" or "Resolved".
    - Each report row has buttons to view details and to handle it.
    - After clicking "Handle", the administrator can update the report's status.

- **FloatingReportsButton.vue**: a global floating button (administrators only).
  - Shown only when the signed-in user is an administrator.
  - Can show the number of pending reports.
  - A shortcut to `AdminReports.vue`.

## Flow

### User reporting flow
1.  The user clicks "Report" on the product page (`ProductDetail.vue`) or a user's page (`UserProductsView.vue`).
2.  The frontend opens a modal (`ReportModal.vue`).
3.  The user chooses a report type and optionally writes a description.
4.  The user clicks "Submit".
5.  `ReportModal.vue` calls `POST /api/reports` to send the report to the backend.
6.  The backend `ReportService.java` stores the report in the `report` table with status `PENDING`.
7.  When the response succeeds, the frontend closes the modal and shows a "Report submitted" message.

### Administrator handling flow
1.  The administrator signs in and sees the pending report count on `FloatingReportsButton.vue`.
2.  The administrator clicks the button or navigates to `/admin/reports` (`AdminReports.vue`).
3.  On load the page calls `GET /api/admin/reports` (filtered to `status=PENDING` by default) and shows every pending report.
4.  The administrator reviews each report's details, including the reason and description, and can follow links to the reported item or user.
5.  Based on the review, the administrator acts (for example delisting the item on the product management page, or banning the user on the user management page).
6.  Afterwards, the administrator clicks "Mark as resolved" or "Mark as invalid" on the report.
7.  The frontend calls `PUT /api/admin/reports/{reportId}/status` to set the report's status to `RESOLVED` or `INVALID`.
8.  The report disappears from the "Pending" list.

## Implementation

### Backend
- **Report.java**: the report entity.
- **ReportMapper.java**: the MyBatis mapper for database access.
- **ReportService.java**: the business logic for reports.
- **ReportController.java**: the API endpoint for users submitting reports.
- **AdminController.java** (or a separate **AdminReportController.java**): the API endpoints for administrators to list and update reports.

### Frontend
- **Global state (Vuex)**: Vuex can control whether `ReportModal.vue` is shown and pass it the ID of the item being reported (such as `productId`).
- **Componentisation**: `ReportModal.vue` is a self-contained component; the parent only controls its visibility and listens for its submit event.
- **Access control**: route guards and `v-if` make sure only administrators can open `AdminReports.vue` and see `FloatingReportsButton.vue`.
