# Local Test Lab guide

## Recommended testing path

Use Test Lab when the normal backend and frontend are already running on your computer. It reuses the current manager login and local database, so no additional validation database, JWT secret, manager account, or handler account is required.

1. Start Poultry Prophet normally on the local computer.
2. Sign in using an existing manager account.
3. In the amber local-workspace banner, select **Test Lab**.
4. Choose an original active batch with at least 20 initial birds.
5. Choose **Realistic** or **Every day**.
6. Select **Generate 100-day test batch**.
7. Open the generated `[TEST COPY]` batch and inspect its dashboard and reports.

The source batch is read-only to the generator. Test Lab creates a separate batch with synthetic events, observations, product records, expenses, income, a completed task, and day-30/day-60/day-100 Selection Review snapshots.

## Profiles

- **Realistic** creates nine observation days and is recommended for consultation or stakeholder demonstration.
- **Every day** creates all 100 observation days and is recommended for chart, history, pagination, and performance testing.

Both profiles create the same population events and finish at `initial population - 10`.

## Server-side safety

Test Lab is not controlled only by the frontend. Before generating anything, the backend verifies both conditions:

- the application environment is `local`, `validation`, or `test`; and
- the PostgreSQL host is local (`localhost`, loopback, or the local Docker database service).

The endpoint is manager-only and requires an explicit synthetic-copy confirmation. A Render or other remote database is rejected even if someone manually opens the Test Lab URL.

## When the isolated Docker workflow is still useful

Use `.env.validation`, snapshot restore, and sanitization only when you need a disposable database, need to import a real database snapshot, or plan destructive tests. That is the advanced safety workflow, not the normal one-click testing path.

## Important research wording

Test Lab results are synthetic technical evidence. They can demonstrate that calculations, histories, dashboards, and reports operate correctly. They cannot be reported as actual farm outcomes, predictive accuracy, treatment effectiveness, or stakeholder validation responses.
