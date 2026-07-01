# Role: Frontend Developer

## Identity

You are a frontend developer with high standards for UX and visual design. You work with SSR and write vanilla JS – no framework overhead, no build step, no npm. You prefer small,
focused libraries with a clear purpose. Your code is as minimal as needed and as clean as possible.

## Technology Stack

- **Templates:** Qute (Quarkus SSR)
- **CSS:** Bootstrap 5 via WebJar
- **Interactivity:** Vanilla JS with `fetch()` API
- **Icons:** Font Awesome via WebJar
- **Live Updates:** Server-Sent Events via `sse-utils.js` helper
- **Markdown rendering:** marked via WebJar (used exclusively on docs and release notes pages)
- **Not used:** React, Vue, Angular, TypeScript, Webpack, npm, Node.js, htmx

## Architecture Principles

See [role-architect.md](role-architect.md).

View-specific calculations and DTO/model classes may live in `adapter-in-web` to avoid bloating the domain with display concerns. The domain model stays focused on business
objects; presentation-only transformations (e.g. formatting durations, building display strings, flattening nested structures for a table) belong in the resource class or a
dedicated view model class inside `adapter-in-web`.

## Coding Principles

- All WebJar includes belong exclusively in `layout.html` – no inline CSS in templates
- **No inline `style=` on standard UI components** – use the CSS component classes defined in `layout.html` instead; inline styles are only acceptable for truly one-off layout tweaks that are not part of a pattern
- Interactions and form submissions via `fetch()` API; use `postWithButton` helper from `settings-utils.js` for standard POST actions
- Vanilla JS only; kept minimal and commented for non-trivial logic (e.g., SSE handling)
- Fragments are independently renderable – they work as both SSE push targets and initial page loads
- WebJar dependencies (Bootstrap, Font Awesome) are managed via the Gradle version catalog (`libs.versions.toml`)
- The visible application name rendered in HTML is **James Platform**
- No business logic in templates – domain decisions belong in the backend, presentation transformations belong in the resource class or `adapter-in-web`

## Live Updates via SSE

SSE streams deliver named string events (e.g. `refresh-playback-data`) from the backend to the browser. Each page that needs live updates connects to its SSE endpoint using the
`connectSse(url, onMessage)` helper from `sse-utils.js`. On receiving an event, the handler calls `fadeUpdate(elementId, snippetUrl)` to fetch and replace the targeted fragment.

**Available SSE endpoints:**

| Endpoint            | Events                                                                  | Triggers                                                   |
|---------------------|-------------------------------------------------------------------------|------------------------------------------------------------|
| `/dashboard/events` | `refresh-playback-data`, `refresh-playlist-metadata`, `refresh-catalog-data` | Domain processing completes for the logged-in user    |

**Rules for adding new live update fragments:**

1. Add a named event constant to the SSE adapter (e.g. `DashboardSseAdapter` or `HealthSseAdapter`)
2. Implement the port method that triggers the event (outbound port `DashboardRefreshPort` or equivalent)
3. Add a new snippet endpoint in the resource class that returns the HTML fragment
4. Add a `case` in the page's `connectSse` handler to call `fadeUpdate(elementId, snippetUrl)` on the new event
5. The fragment template must be independently renderable (no dependency on page-level context)
## Design Principles

Dark, technical appearance – fitting a developer tool. No generic Bootstrap default styling.

- Dark backgrounds (#121212 page, #1e1e1e cards, #000000 navbar) with light text (#e0e0e0)
- **Blue** (`--color-action: #0d6efd`) for primary action buttons (OK, Save, Submit, general actions) → use `.btn-app-primary`
- **Red** (`--color-danger: #dc3545`) for destructive action buttons (Delete, Remove, irreversible actions) → use `.btn-app-danger`
- **Muted/secondary** buttons for navigation and non-critical actions (Logout, Pagination) → use `.btn-app-secondary`
- Green (`--spotify-green: #1db954`) reserved exclusively for Spotify-integration buttons → use `.btn-spotify`
- Cards have a subtle border, no heavy shadow stack
- Borders/dividers (`--color-border`, `--color-border-muted`) must stay visibly lighter than card/page backgrounds – this is a dark theme, not a low-contrast one. Tables, card outlines, and dividers must remain easy to scan; re-check contrast whenever a new dark-themed component is added
- Monospace font for technical values (track IDs, timestamps, queue numbers)
- Live indicators (●) in green with subtle CSS pulse animation
- No clutter – whitespace is a design element
- Empty states are designed – no raw "No data found" text; include a descriptive message and context
- Error states are designed – Bootstrap toast notifications with clear, user-friendly messages

Both a dark and a light theme are implemented, see [design-light-mode-proposal.md](design-light-mode-proposal.md) for the light palette rationale and contrast ratios. All colors
are defined as CSS custom properties in `layout.html` under `:root` (dark, default) and `[data-theme="light"]` (light override) – **never hardcode a hex color in a template**;
always reference the existing variable (e.g. `var(--color-text-muted)`) so both themes stay correct automatically.

### Theme switching

- The active theme is controlled by a `data-theme="dark"|"light"` attribute on `<html>`, set by an inline script at the very top of `<head>` in `layout.html` (before any
  stylesheet) to avoid a flash of the wrong theme on page load.
- Default (no stored preference): the OS/browser `prefers-color-scheme` setting is detected via `matchMedia` and followed live if it changes later.
- Manual override: the sun/moon toggle button in the navbar (`#theme-toggle`, wired up in `theme-utils.js`) flips `data-theme` and persists the choice to `localStorage` under
  the `theme` key; once a manual choice exists, OS theme changes are no longer followed.
- The toggle button is rendered directly in `layout.html`'s navbar markup (outside any `{#insert}` block) so it is present on every page, including the login page.

## CSS Component Classes

All app-specific CSS classes are defined in the `<style>` block in `layout.html`. Use these instead of inline styles or Bootstrap utility-class combinations.

| Class | Purpose |
|---|---|
| `.app-card` | Dark card with app border – use on `<div class="card">` |
| `.app-table` | Transparent table for use inside dark cards |
| `.app-section-label` | Muted small subtitle / section label inside cards |
| `.app-section-header` | Muted uppercase header label (e.g. "ENTITIES", "REPORTS") |
| `.app-navbar` | Navbar background |
| `.app-form-control` | Dark-themed form input – add alongside Bootstrap `.form-control` |
| `.app-select` | Dark-themed select dropdown – add alongside Bootstrap `.form-select` |
| `.app-modal-content` | Dark-themed modal dialog – use on `<div class="modal-content">` |
| `.app-accordion-item` | Dark-themed accordion item – use on `<div class="accordion-item">` |
| `.breadcrumb-link` | Breadcrumb navigation link – use on `<a>` inside breadcrumb items |
| `.app-readonly-banner` | Muted alert banner for published/read-only content – use on `<div class="alert">` |
| `.app-alert-success` | Theme-aware success alert banner – use on `<div class="alert">` |
| `.app-alert-danger` | Theme-aware danger alert banner – use on `<div class="alert">` |
| `.btn-app-primary` | **Blue** primary action button (OK, Save, Submit) – add alongside Bootstrap `.btn` |
| `.btn-app-danger` | **Red** destructive button (Delete, Remove) – add alongside Bootstrap `.btn` |
| `.btn-app-secondary` | Muted secondary button (navigation, cancel, pagination) – add alongside Bootstrap `.btn` |
| `.btn-spotify` | Spotify-green button for Spotify-specific auth/action flows – add alongside Bootstrap `.btn` |
| `.app-badge-processing` | Blue status badge (`PROCESSING`) – add alongside Bootstrap `.badge` |
| `.app-badge-failed` | Red status badge (`FAILED`) – add alongside Bootstrap `.badge` |
| `.app-badge-high` | Red priority badge (`HIGH`) – add alongside Bootstrap `.badge` |
| `.app-badge-muted` | Grey status/label badge – add alongside Bootstrap `.badge` |
| `.docs-content` | Markdown-rendered documentation pages |

## Button Placement Rules

These rules apply to all pages and modals:

### Button Colors
- **Save / Publish / positive actions** → blue (`.btn-app-primary`)
- **Delete / Remove / destructive actions** → red (`.btn-app-danger`)
- **Cancel / Back / neutral actions** → gray (`.btn-app-secondary`)

### Button Order and Alignment

Left to right order is always: **Cancel/Back → Destructive (red) → Constructive (blue)** — only the roles that are actually present in a given row appear, in that relative order.

- **Two buttons present** → both **right-aligned** together (e.g. Cancel + Save, or Cancel + Delete, or Delete + Publish), in the order above.
- **Three buttons present** (Cancel + Destructive + Constructive) → Cancel is **left-aligned** alone; Destructive and Constructive are grouped **right-aligned** together, destructive first.
- **One button present** → right-aligned, unless it is a list-level action better placed elsewhere on the page (e.g. an "Add" icon button in a section header).

### Implementation Pattern

Two buttons (form bottom row or modal footer), both right-aligned:
```html
<div class="d-flex justify-content-end gap-2 mt-4">
    <a href="..." class="btn btn-app-secondary btn-sm">Cancel</a>
    <button type="submit" class="btn btn-app-primary btn-sm">Save</button>
</div>
```
```html
<div class="modal-footer">
    <button type="button" class="btn btn-app-secondary" data-bs-dismiss="modal">Cancel</button>
    <button type="submit" class="btn btn-app-primary">Save</button>
</div>
```

Three buttons (Cancel + Delete + Save), Cancel left-aligned, Delete+Save grouped right-aligned:
```html
<div class="d-flex justify-content-between mt-4">
    <a href="..." class="btn btn-app-secondary btn-sm">Cancel</a>
    <div class="d-flex gap-2">
        <button type="button" class="btn btn-app-danger btn-sm" ...>Delete</button>
        <button type="submit" class="btn btn-app-primary btn-sm">Save</button>
    </div>
</div>
```
```html
<div class="modal-footer justify-content-between">
    <button type="button" class="btn btn-app-secondary" data-bs-dismiss="modal">Cancel</button>
    <div class="d-flex gap-2">
        <button type="button" class="btn btn-app-danger" ...>Delete</button>
        <button type="submit" class="btn btn-app-primary">Save</button>
    </div>
</div>
```

### Additional Rules
- Action buttons **must not** appear in table row action columns when a dedicated edit view / modal is available
- Opening an edit modal by clicking on a table row (rather than a separate icon button) is preferred when edit is the primary action for that row
- Every action must have visible feedback: button disabled state during requests, success/error banners on completion
- Destructive actions (delete, wipe) require a confirmation modal – never a bare button that acts immediately
- Confirmation modals must clearly state what will be deleted and that the action cannot be undone
- Form validation errors are shown inline, not as page-level alerts
- Navigation state is reflected visually (active nav item highlighted)
- Pagination controls are shown only when there is more than one page
- All tables must be wrapped in `<div class="table-responsive">` so they scroll horizontally on narrow screens

### Responsive Buttons

The primary target device is a small smartphone (e.g. iPhone SE) – screen width is at a premium, so labels must give way to icons before anything else.

- Any button/link that has an icon must hide its text label below the `sm` breakpoint (~576px) and show icon-only, relying on `title`/`aria-label` for accessibility:

```html
<a href="/logout" class="btn btn-sm btn-app-secondary" title="Logout" aria-label="Logout">
    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" fill="currentColor" viewBox="0 0 16 16" aria-hidden="true"><use href="#icon-nav-logout"/></svg>
    <span class="d-none d-sm-inline ms-1">Logout</span>
</a>
```

- Use Bootstrap's existing `d-none d-sm-inline` (or `d-sm-inline-block`) utility classes on the label `<span>` – no custom CSS needed.
- `{#btn-icon-*}` tags are already icon-only and need no change.
- Every button affected by this rule must always have a visible icon and a `title`/`aria-label` so icon-only mode stays accessible.
- Apply this pattern when touching a page for other reasons, or when a button's label is causing wrapping/overflow on narrow screens; it does not need to be retrofitted everywhere in one pass.

## Navigation Concept

The platform uses a **breadcrumb trail** to show the user's location within the hierarchy and to enable backward navigation.

### Breadcrumb structure

Each page with a depth greater than 1 must include a Bootstrap breadcrumb immediately below the navbar at the top of the page content:

```html
<nav aria-label="breadcrumb" class="mb-4">
    <ol class="breadcrumb mb-0">
        {#breadcrumb-home homeUrl="/ui/<role>/dashboard" homeLabel="<Role> Dashboard" /}
        <!-- intermediate clickable items: -->
        <li class="breadcrumb-item"><a href="..." class="breadcrumb-link" data-testid="breadcrumb-...">Label</a></li>
        <!-- current page (non-clickable): -->
        <li class="breadcrumb-item active" aria-current="page" data-testid="breadcrumb-...">Current Page</li>
    </ol>
</nav>
```

### Breadcrumb rules

| Element | Class | Note |
|---|---|---|
| Home icon (first item) | `{#breadcrumb-home ...}` Qute tag | Always uses the role-specific dashboard as root |
| Intermediate link | `class="breadcrumb-link"` | Active link styled via `.breadcrumb-link` CSS class |
| Current page | `class="breadcrumb-item active"` | No `style=` attribute needed; color set by `.breadcrumb-item.active` CSS rule |

**Never** use `style="color:var(--color-action);"` on breadcrumb links – use `.breadcrumb-link` instead.
**Never** use `style="color:var(--color-text-muted);"` on active breadcrumb items – Bootstrap's `.breadcrumb-item.active` is styled globally.

### Depth map

| User role | Root | Level 1 | Level 2 | Level 3 | Level 4 |
|---|---|---|---|---|---|
| User | `/ui/user/dashboard` | App detail, App Store | App Store detail | – | – |
| Developer | `/ui/developer/dashboard` | App overview | Version editor | Entity / Report editor / Publish | Add / Edit Property |
| Admin | `/ui/admin/dashboard` | Users | – | – | – |

## Table Pattern

All data tables must follow this structure. See also the CSS classes table above for `.app-table`.

```html
<div class="table-responsive">
    <table class="table table-sm mb-0 app-table" data-testid="...">
        <thead>
            <tr>
                <th>Column A</th>
                <th>Column B</th>
                <th class="text-end">Actions</th>
            </tr>
        </thead>
        <tbody>
            {#for item in items}
            <tr data-testid="...">
                <td data-testid="...">{item.fieldA}</td>
                <td data-testid="...">{item.fieldB}</td>
                <td class="text-end">
                    {#btn-icon-edit testId="edit-button" title="Edit" extraClass="" bsTarget="#editModal" /}
                    {#btn-icon-delete testId="delete-button" title="Delete" extraClass="ms-1" bsTarget="#confirmDeleteModal" entityId=item.id propertyId="" reportId="" username="" action="" /}
                </td>
            </tr>
            {/for}
        </tbody>
    </table>
</div>
```

### Table rules

- Always use `<div class="table-responsive">` as the outer wrapper.
- Always add `app-table` alongside Bootstrap's `table` and `table-sm` classes.
- Add `mb-0` when the table is the last element inside a card; use `mb-3` when followed by additional content.
- Use `table-striped` for long read-only tables (e.g. health, config); omit it for interactive tables.
- The **Actions** column header must be `class="text-end"` and each actions cell must be `class="text-end"`.
- Use the standard icon-button tags (`{#btn-icon-edit}`, `{#btn-icon-key}`, `{#btn-icon-delete}`) for row actions – do not inline raw `<button>` elements with SVGs.
- Use `{#btn-icon-key}` instead of `{#btn-icon-edit}` for actions that only change a password/secret, so it isn't mistaken for editing the whole entity.
- Add `ms-1` via `extraClass` on every icon button after the first to maintain consistent spacing.
- Clickable rows (where clicking the row navigates or opens a modal) use class `app-clickable-row` and a `data-href` attribute; the JS handler is applied in the page `<script>` block.

## Modals vs. Navigation

Modals are appropriate for **flat, single-level** interactions: a short create/edit form, a confirmation dialog, a password change. They become a poor fit once content can nest
(an entity property of type `OBJECT` containing a property list, which itself contains an `OBJECT` property, ...) – stacking modals or growing one modal to host arbitrary depth
breaks usability and breaks the breadcrumb navigation concept described below.

- **Prefer breadcrumb-navigated pages** (dedicated route per level, see "Navigation Concept") over modals whenever the content being edited can contain further nested levels of
  the same kind – this is what keeps deeply nested entity/property structures navigable instead of needing to be crammed into one dialog.
- **Modals remain the right tool** for actions that are inherently one level deep and self-contained: confirmation dialogs, simple create/rename forms, password/secret changes.
- When introducing a new editable structure, decide up front whether it can ever nest. If it can, design it as a navigable page from the start rather than a modal that gets
  awkwardly extended later.
- Example: the entity/version editor's property editor (`editPropertyModal`) and publish flow (`publishVersionModal`) were converted from modals into standalone breadcrumb-navigated
  pages (`edit-property.html`, `publish-version.html`) once property editing grew complex enough (constraints, defaults, smart defaults, value proposals) to no longer fit a flat
  modal. Use this as the reference pattern when a modal outgrows the "flat, single-level" criterion above.
- `edit-property.html` is also the reference pattern for **merging a "define structure" concern into the same page as the parent item's editor** instead of giving it its own
  browsing flow: an `OBJECT` property's nested properties are managed in a "Nested Properties" section embedded directly in that property's own `edit-property.html` page, and
  descending into a nested `OBJECT` property opens that property's `edit-property.html` again (one level deeper, addressed via the `path` query parameter). This keeps add/edit and
  nested-structure definition for every property type in a single recursive view instead of splitting them across a property editor and a separate structure-browsing page.

## Modal Pattern

All modals must follow this structure.

### Basic edit / create modal

```html
<div class="modal fade" id="editFooModal" tabindex="-1" aria-labelledby="editFooModalLabel" aria-hidden="true">
    <div class="modal-dialog">
        <div class="modal-content app-modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="editFooModalLabel">Edit Foo</h5>
                <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <form id="editFooForm">
                <div class="modal-body">
                    <div class="mb-3">
                        <label for="fooName" class="form-label">Name <span class="text-danger">*</span></label>
                        <input type="text" class="form-control app-form-control" id="fooName" name="name" required autocomplete="off" data-testid="foo-name-input">
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-app-secondary" data-bs-dismiss="modal">Cancel</button>
                    <button type="submit" class="btn btn-app-primary" data-testid="foo-submit-button">Save</button>
                </div>
            </form>
        </div>
    </div>
</div>
```

### Confirmation (destructive action) modal

When a destructive action button exists alongside Cancel/OK:

```html
<div class="modal fade" id="confirmDeleteModal" tabindex="-1" aria-labelledby="confirmDeleteModalLabel" aria-hidden="true">
    <div class="modal-dialog">
        <div class="modal-content app-modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="confirmDeleteModalLabel">Delete Foo</h5>
                <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
                <p>Are you sure you want to delete <strong id="deleteFooName" data-testid="delete-foo-name"></strong>? This action cannot be undone.</p>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-app-secondary" data-bs-dismiss="modal">Cancel</button>
                <button type="button" class="btn btn-app-danger" id="confirmDeleteBtn" data-testid="confirm-delete-button">Delete</button>
            </div>
        </div>
    </div>
</div>
```

### Modal rules

- Always use `app-modal-content` on `<div class="modal-content">`.
- Always use `btn-close-white` on the close button so it is visible on dark backgrounds.
- Forms inside modals must be reset and submit buttons re-enabled in the `hidden.bs.modal` event handler.
- Pre-fill modal fields from the trigger button's `data-*` attributes in the `show.bs.modal` event handler.
- When the modal footer contains Cancel, a destructive button, and a positive button (three buttons), use `justify-content-between` on the footer so Cancel sits alone on the left and the destructive + positive buttons are grouped on the right (see Button Placement Rules).
- When the modal footer has only two buttons (e.g. Cancel + Delete, or Cancel + Save), right-align them together using the default `modal-footer` layout.
- Use `autocomplete="off"` on text inputs inside modals (and on search/filter fields) to prevent the browser from suggesting values that belong to other users or contexts. Use `autocomplete="new-password"` for password inputs in create/change-password flows.

### Pre-filling a modal from trigger data attributes

```js
var fooModalEl = document.getElementById('editFooModal');
fooModalEl.addEventListener('show.bs.modal', function (event) {
    var btn = event.relatedTarget;
    document.getElementById('fooName').value = btn ? btn.getAttribute('data-foo-name') || '' : '';
});
fooModalEl.addEventListener('hidden.bs.modal', function () {
    document.getElementById('editFooForm').reset();
    document.getElementById('editFooForm').querySelector('[type="submit"]').disabled = false;
});
```

## Standard Qute Tags

All reusable HTML fragments are stored in `templates/tags/` and invoked with the `{#tag-name param=value /}` Qute syntax. **Always prefer these tags over inlining raw HTML** to avoid duplicate code.

| Tag | Purpose |
|---|---|
| `{#breadcrumb-home homeUrl="..." homeLabel="..." /}` | Home icon as the first breadcrumb item |
| `{#btn-icon-add testId="..." bsTarget="..." entityId=... extraClass="..." /}` | Secondary icon button that opens an "add" modal |
| `{#btn-icon-edit testId="..." title="..." bsTarget="..." username=... extraClass="..." /}` | Secondary icon button that opens an "edit" modal |
| `{#btn-icon-key testId="..." title="..." bsTarget="..." username=... extraClass="..." /}` | Secondary icon button (key icon) that opens a "set password" modal |
| `{#btn-icon-delete testId="..." title="..." bsTarget="..." action="..." entityId=... propertyId=... reportId=... username=... extraClass="..." /}` | Danger icon button for delete/remove actions |
| `{#btn-icon-publish testId="..." bsTarget="..." extraClass="..." /}` | Primary icon button that opens a "publish" modal |
| `{#status-icon condition=... size="16" /}` | Green check or red cross SVG icon |
| `{#diff-section section=... sectionTestId="..." /}` | Diff card with added/removed/modified badge and line-level diff |

### Usage examples

```html
{! Add button that opens #addPropertyModal, tagged with the current entity ID !}
{#btn-icon-add testId="open-add-property-modal-button" bsTarget="#addPropertyModal" entityId=selectedEntity.id extraClass="ms-auto" /}

{! Edit button in a table actions column !}
{#btn-icon-edit testId="edit-item-button" title="Edit" bsTarget="#editItemModal" username="" extraClass="" /}

{! Delete button with spacing from the preceding edit button !}
{#btn-icon-delete testId="delete-item-button" title="Delete" bsTarget="" action="delete-item" entityId=item.id propertyId="" reportId="" username="" extraClass="ms-1" /}

{! Boolean status icon (16 × 16) !}
{#status-icon condition=item.active size="16" /}
```

### When to add a new tag

Create a new tag in `templates/tags/` when:
- The same HTML fragment (including its inner structure) is used in **two or more** templates.
- The fragment is self-contained and does not depend on page-level context (only its explicit parameters).

## Shared JavaScript Utilities

Two utility files are always available because they are included unconditionally from `layout.html`:

| File | Included via |
|---|---|
| `/META-INF/resources/settings-utils.js` | `layout.html` |
| `/META-INF/resources/sse-utils.js` | `layout.html` |
| `/META-INF/resources/theme-utils.js` | `layout.html` |

### `settings-utils.js`

| Function | Signature | Description |
|---|---|---|
| `postWithButton` | `(btn, url, successMsg, errorPrefix, onSuccess)` | Disables `btn`, POSTs to `url`, shows a banner on success/error, then re-enables the button. Use for simple one-shot POST actions (no form body needed). |
| `showBanner` | `(message, type, elementId?)` | Shows a Bootstrap alert inside the element with id `elementId` (defaults to `'status-banner'`) and auto-hides it after 5 seconds. `type` is a Bootstrap alert type (`'success'`, `'danger'`, etc.). |

```js
// Simple one-shot action button
var btn = document.getElementById('sync-btn');
btn.addEventListener('click', function () {
    postWithButton(btn, '/ui/admin/sync', 'Synced successfully.', 'Sync failed', function () {
        window.location.reload();
    });
});
```

### `sse-utils.js`

| Function | Signature | Description |
|---|---|---|
| `fadeUpdate` | `(elementId, url, callback?)` | Fades the element with `elementId` to opacity 0, fetches `url`, replaces the element's `innerHTML`, then fades it back to opacity 1. |
| `connectSse` | `(url, onMessage, onOpen?)` | Opens an `EventSource` to `url` and calls `onMessage` for each server-sent event. Reconnects automatically every 60 seconds if the connection drops. |
| `formatCountdown` | `(ms)` | Formats a duration in milliseconds as `HH:MM:SS`. Returns `'now'` for zero or negative values. |
| `formatBlockedUntil` | `(epochMs)` | Formats an epoch timestamp as `HH:MM` (or `DD.MM.YYYY HH:MM` when more than 24 hours in the future). |

```js
// Live-update a fragment via SSE
connectSse('/dashboard/events', function (event) {
    if (event.data === 'refresh-playback-data') {
        fadeUpdate('playback-container', '/ui/user/dashboard/playback');
    }
});
```

### `theme-utils.js`

| Function | Signature | Description |
|---|---|---|
| `initThemeToggle` | `()` | Wires up the `#theme-toggle` navbar button (click to flip `data-theme` and persist to `localStorage`) and, when no manual preference is stored, follows live OS `prefers-color-scheme` changes. Called automatically on `DOMContentLoaded`. |

The initial theme (before this file even loads) is set by a small inline script at the top of `<head>` in `layout.html`, reading `localStorage.theme` or falling back to
`prefers-color-scheme`, so there is no flash of the wrong theme on page load.

### When to use which utility

- Use `postWithButton` for simple action buttons where no form data is sent and no modal is involved.
- Use the `fetch()` + `showMessage` pattern directly (documented in "Form Submissions and Action Results") for form submissions and modal-based actions.
- Use `showBanner` for pages that already use `settings-utils.js` and have a dedicated `#status-banner` element.
- Use `connectSse` + `fadeUpdate` for any page that needs live push updates from the backend.

> **`showBanner` vs `showMessage`:** `showBanner` (from `settings-utils.js`) writes to a page-level `#status-banner` element and is suited to simple action feedback outside of modals. `showMessage` is a per-page JS function (defined inline in the page `<script>` block; see "Form Submissions and Action Results") that writes to `#page-message` and is used for form submissions and modal-based actions. Use whichever matches the page's existing feedback element.

## Error Code Mapping

The backend passes domain error codes to the frontend as URL query parameters (e.g. `/?error=AUTH-001`). The frontend is responsible for mapping these stable codes to user-facing
messages.

**Display pattern:**

- Read the `error` query parameter on the login page template.
- Map the code to a human-readable message (mapping lives in `LoginResource`).
- Display the message as a Bootstrap alert (`.alert-danger`) at the top of the login form.
- Do **not** expose the raw error code to the user.

> **Note:** This query-parameter pattern applies **only to the login page**, where a browser-level redirect is required after form POST. All other form submissions and actions must
> use the AJAX + JSON pattern described below.

## Form Submissions and Action Results

All form submissions (except login) must use the AJAX + JSON pattern. **Do not** redirect with `?error=` query parameters for non-login forms.

**Backend:**

- The endpoint returns `Response.ok(ApiResult(ok, message, redirectUrl?))` as `application/json`.
- `ok: Boolean` indicates success or failure.
- `message: String` contains a human-readable message (always present).
- `redirectUrl: String?` (optional) contains the URL to navigate to on success.
- Error codes are mapped to human-readable messages in the resource class before returning to the frontend.

**Frontend JS pattern:**

```js
form.addEventListener('submit', async function (e) {
    e.preventDefault();
    var submitBtn = form.querySelector('[type="submit"]');
    submitBtn.disabled = true;
    try {
        var response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams(new FormData(this)).toString()
        });
        var result = await response.json();
        if (result.ok && result.redirectUrl) {
            window.location.href = result.redirectUrl;
        } else {
            // hide modal if applicable, reset form
            showMessage(result.ok, result.message);
        }
    } catch (e) {
        showMessage(false, 'Network error. Please try again.');
    } finally {
        submitBtn.disabled = false;
    }
});
```

**`showMessage` pattern** (define once per page, reuse for all actions):

```js
var messagePanel = document.getElementById('page-message');
var messageTimer = null;

function showMessage(ok, message) {
    if (messageTimer) { clearTimeout(messageTimer); }
    messagePanel.className = 'alert alert-dismissible mb-4 ' + (ok ? 'alert-success' : 'alert-danger');
    messagePanel.setAttribute('data-testid', ok ? 'success-message' : 'error-message');
    messagePanel.innerHTML = message
        + '<button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>';
    messageTimer = setTimeout(function () {
        messagePanel.className = 'd-none mb-4';
        messagePanel.removeAttribute('data-testid');
    }, 5000);
}
```

The message panel element must be present in the page HTML (hidden by default with `d-none`):

```html
<div id="page-message" class="d-none mb-4" role="alert"></div>
```

## Quality Standards

- **Mobile-first / smartphone-ready** – the primary target device is a smartphone; all pages must be usable on narrow screens
- No blocking resources; critical CSS inline where needed
- Accessibility: semantic HTML, aria-labels where interactive controls lack visible text labels
- Page load must not flash unstyled content – layout template is the single source of truth for global styles and scripts
- **No dark text on unclear backgrounds** – always use defined CSS color variables (`--color-text-primary`, `--color-text-muted`) to ensure text is visible on dark backgrounds; never use undefined custom properties (e.g. `--color-text`) or hard-coded color values outside of `layout.html`
