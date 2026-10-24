# Application features

Beyond forms, grids and CRUD screens, RapidSwing has the features that larger business applications need. Each
one has tests and is used in the demo app; the last column says where.

## Navigation and application services

| Feature | API | In the demo |
|---|---|---|
| Navigation tree of folders and views; hides views the user may not open and follows the current view | `NavigationTree`, `MainFrame.setNavigator` | Tree left of the views |
| HTML pages, e.g. folder landing pages, with links to views (`view:customers`) and the web | `HtmlView`, `NavigationTree.Folder.page` | Sales folder page |
| Jump to any view or menu command by typing part of its name, `*` as a wildcard | `QuickOpen`, `MainFrame.enableQuickOpen` (Ctrl+K) | Ctrl+K |
| Open a view in a window of its own | `ViewManager.create`, `DetachedWindow`, `StandardActions.newWindow` | View > Open in New Window |
| Context help: an HTML page per view, per language | `HelpService` | Help > Help (F1) |
| List of keyboard shortcuts | `ShortcutsDialog` | Help > Keyboard Shortcuts |
| System information (application, user, sign-in time, Java, memory) with copy to clipboard | `SystemInfo` | Help > System Information |
| Users change their own password | `PasswordChanger`, `ChangePasswordDialog` | File > Change password |
| App-wide display formats; translated enum names | `Formats` | Order status, product category |
| Messages users understand, per exception class | `ErrorMessages` | Connection errors |
| Hooks for signing out and for unexpected errors | `SwingApp.onLogout`, `SwingApp.onRuntimeError` | `onRuntimeError` (default) |
| Background work with progress and Cancel | `Progress`, `TaskService.callWithProgress`, `ProgressDialog` | Import wizard steps |

## Data screens

| Feature | API | In the demo |
|---|---|---|
| Search with an operator per field (contains, between, is empty, ...) and recent searches | `Query`, `Filter`, `Operator`, `SearchPanel` | Customers search panel |
| Paging; searching, sorting and paging on the server | `QueryRepository`, `Page`, `PagingBar`, `InMemoryRepository` | Customers paging bar |
| Actions on selected rows: selection rule, per-row condition, confirmation, right-click menu | `DataGrid.rowAction` | Deactivate, Compare, Print |
| Compare rows side by side, differences marked | `CompareDialog`, `DataGrid.addCompareAction` | Customers > Compare |
| Print a list | `DataGrid.print(title)` | Customers > Print |
| Cell and row colors for light and dark themes | `CellStyle`, `ColumnBuilder.style`, `DataGrid.rowStyle` | Large balances, inactive rows, overdue orders |
| Detail table of the edited item | `CrudScreen.Builder.details` | Orders under the customer form |
| Refresh on a timer or when an event is published | `CrudScreen.Builder.refreshEvery`, `refreshOn` | Customers refresh after an import |
| A complete CRUD screen for any JavaBean class, at run time | `BeanScreens` | Sales > Products |

## Forms and other screens

| Feature | API | In the demo |
|---|---|---|
| Warnings that do not block saving | `FieldBuilder.warn` | Negative balance, unusual age |
| Wizards: pages, background steps with progress, outcomes (next, jump, error, finish) | `Wizard`, `Outcome` | Tools > Import customers |
| Keyboard-only screens with function keys | `FunctionKeyBar` | Warehouse > Picking |
| Bar, line and donut charts, with tooltips and a table view | `BarChart`, `LineChart`, `PieChart` | Dashboard |
| User settings with a dialog, import/export and defaults | `Settings`, `SettingsDialog` | Tools > Settings |
