# Security gate exceptions

The Jenkins pipeline stops a release when Dependency-Check or Trivy finds a High or Critical vulnerability. An exception lets a build continue for a limited time when a finding can't be fixed straight away.

Fixing the vulnerability is always the first choice. Exceptions are also used for false positives, where a scanner has flagged something that doesn't apply to this app.

## Rules

- Every exception needs a justification, an owner, an approver and an expiry date.
- The owner and the approver must be different people.
- The longest allowed period is **14 days for Critical** and **30 days for High**.
- Exceptions can't be renewed automatically. To extend one, submit a new request with an updated justification.

**Approver:** team lead. *(Proposed. The team still needs to confirm this and the time limits.)*

## How to request an exception

1. Create a Jira ticket with the vulnerability ID (CVE), the affected package, its severity and the reason it can't be fixed now.
2. Add a row to the register below, using the next EX number.
3. Add the entry to the file for the scanner that reported it, using the same expiry date. If both scanners report it, add it to both.
   - Dependency-Check: `security/dependency-check-suppressions.xml`
   - Trivy: `.trivyignore`
4. Open a pull request and link the Jira ticket.
5. The exception takes effect when the approver approves and merges the pull request.

## When an exception expires

The scanner stops applying the exception on the expiry date, so the next build fails if the vulnerability is still there. The owner must then fix it or submit a new request. Once it's fixed or no longer needed, remove its entry from the scanner file and set its status to Closed.

## Register

| ID | CVE | Scanner | Package or image | Severity | Justification | Owner | Approver | Approved on | Expires on | Jira | Status |
|---|---|---|---|---|---|---|---|---|---|---|---|
| | | | | | | | | | | | |
