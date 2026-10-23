
/* =========================================================
   PRIVACY SHIELD - ADMIN JAVASCRIPT
   Features:
   1. Admin login
   2. User management
   3. Session management
   4. Force-cleanup sessions
   5. Audit log viewer
   6. Admin logout
   ========================================================= */


/* =========================================================
   ADMIN LOGIN
   ========================================================= */

async function adminLogin() {
    const username =
        document.getElementById("adminUsername")?.value.trim();

    const password =
        document.getElementById("adminPassword")?.value;

    const message =
        document.getElementById("adminLoginMessage");

    if (!username || !password) {
        if (message) {
            message.textContent =
                "Please enter username and password.";
        }
        return;
    }

    if (password.length < 8) {
        if (message) {
            message.textContent =
                "Password must be at least 8 characters.";
        }
        return;
    }

    try {
        const response = await fetch("/api/admin/login", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            credentials: "same-origin",
            body: JSON.stringify({ username, password })
        });

        const data = await response.json().catch(() => ({}));

        if (!response.ok || data.role !== "ADMIN") {
            if (message) {
                message.textContent =
                    data.message || "Admin login failed.";
            }
            return;
        }

        window.location.href = "/admin-dashboard.html";

    } catch (error) {
        console.error("ADMIN LOGIN ERROR:", error);

        if (message) {
            message.textContent =
                "Unable to connect to server.";
        }
    }
}


/* =========================================================
   BACK TO USER LOGIN
   ========================================================= */

function goToUserLogin() {
    window.location.href = "/index.html";
}


/* =========================================================
   SHARED MESSAGE DISPLAY
   ========================================================= */

function showAdminMessage(text, isError = false) {
    const message = document.getElementById("adminMessage");

    if (!message) {
        return;
    }

    message.textContent = text;
    message.style.color = isError ? "#c62828" : "#198754";
}


/* =========================================================
   SHARED TABLE HELPERS
   ========================================================= */

function createAdminTable(headers) {
    const table = document.createElement("table");

    table.style.width = "100%";
    table.style.borderCollapse = "collapse";

    const thead = document.createElement("thead");
    const headerRow = document.createElement("tr");

    headers.forEach(function (heading) {
        const th = document.createElement("th");

        th.textContent = heading;
        th.style.padding = "10px";
        th.style.textAlign = "left";
        th.style.borderBottom = "1px solid #ddd";

        headerRow.appendChild(th);
    });

    thead.appendChild(headerRow);

    const tbody = document.createElement("tbody");

    table.appendChild(thead);
    table.appendChild(tbody);

    return { table, tbody };
}


function addAdminCell(row, value) {
    const cell = document.createElement("td");

    cell.textContent =
        value === null || value === undefined ? "" : String(value);

    cell.style.padding = "10px";
    cell.style.borderBottom = "1px solid #ddd";

    row.appendChild(cell);

    return cell;
}


function createAdminButton(label, backgroundColor, onClick) {
    const button = document.createElement("button");

    button.type = "button";
    button.textContent = label;
    button.style.backgroundColor = backgroundColor;
    button.style.color = "#fff";
    button.style.border = "none";
    button.style.padding = "8px 12px";
    button.style.borderRadius = "5px";
    button.style.cursor = "pointer";

    button.addEventListener("click", onClick);

    return button;
}


/* =========================================================
   LOAD USERS
   ========================================================= */

async function loadUsers() {
    const userList = document.getElementById("userList");

    if (!userList) {
        return;
    }

    userList.textContent = "Loading users...";

    try {
        const response = await fetch("/api/admin/users", {
            method: "GET",
            credentials: "same-origin"
        });

        if (!response.ok) {
            userList.textContent =
                response.status === 401 || response.status === 403
                    ? "Admin authentication required. Please log in again."
                    : "Unable to load users.";

            return;
        }

        const users = await response.json();

        if (!Array.isArray(users) || users.length === 0) {
            userList.textContent = "No users found.";
            return;
        }

        const wrapper = document.createElement("div");
        wrapper.style.overflowX = "auto";

        const { table, tbody } = createAdminTable([
            "ID",
            "Username",
            "Role",
            "Status",
            "Actions"
        ]);

        users.forEach(function (user) {
            const row = document.createElement("tr");

            addAdminCell(row, user.userId);
            addAdminCell(row, user.username);
            addAdminCell(row, user.role);
            addAdminCell(row, user.status);

            const actionsCell = document.createElement("td");
            actionsCell.style.padding = "10px";

            if (user.role === "ADMIN") {
                actionsCell.textContent = "Admin account";
            } else if (
                user.status === "ACTIVE" ||
                user.status === "INACTIVE"
            ) {
                const newStatus =
                    user.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";

                const button = createAdminButton(
                    user.status === "ACTIVE" ? "Deactivate" : "Activate",
                    user.status === "ACTIVE" ? "#dc3545" : "#198754",
                    function () {
                        changeUserStatus(user.userId, newStatus);
                    }
                );

                actionsCell.appendChild(button);
            } else {
                actionsCell.textContent = "Unknown status";
            }

            row.appendChild(actionsCell);
            tbody.appendChild(row);
        });

        wrapper.appendChild(table);
        userList.replaceChildren(wrapper);

    } catch (error) {
        console.error("LOAD USERS ERROR:", error);
        userList.textContent = "Error loading users.";
    }
}


/* =========================================================
   ACTIVATE / DEACTIVATE USER
   ========================================================= */

async function changeUserStatus(userId, newStatus) {
    const action =
        newStatus === "ACTIVE" ? "activate" : "deactivate";

    if (!window.confirm(
        `Are you sure you want to ${action} user ID ${userId}?`
    )) {
        return;
    }

    try {
        const response = await fetch(
            `/api/admin/users/${encodeURIComponent(userId)}/status?status=${encodeURIComponent(newStatus)}`,
            {
                method: "PUT",
                credentials: "same-origin"
            }
        );

        const data = await response.json().catch(() => ({}));

        if (!response.ok) {
            throw new Error(
                data.message || `Unable to ${action} user.`
            );
        }

        showAdminMessage(
            `User ${userId} status changed to ${newStatus}.`
        );

        await loadUsers();

    } catch (error) {
        console.error("USER STATUS UPDATE ERROR:", error);
        showAdminMessage(error.message, true);
    }
}


/* =========================================================
   LOAD SESSIONS
   ========================================================= */

async function loadSessions() {
    const sessionList = document.getElementById("sessionList");

    if (!sessionList) {
        return;
    }

    sessionList.textContent = "Loading sessions...";

    try {
        const response = await fetch("/api/admin/sessions", {
            method: "GET",
            credentials: "same-origin"
        });

        if (!response.ok) {
            sessionList.textContent =
                response.status === 401 || response.status === 403
                    ? "Admin authentication required. Please log in again."
                    : "Unable to load sessions.";

            return;
        }

        const sessions = await response.json();

        if (!Array.isArray(sessions) || sessions.length === 0) {
            sessionList.textContent = "No sessions found.";
            return;
        }

        const wrapper = document.createElement("div");
        wrapper.style.overflowX = "auto";

        const { table, tbody } = createAdminTable([
            "Session ID",
            "User ID",
            "Status",
            "Start Time",
            "End Time",
            "Actions"
        ]);

        sessions.forEach(function (session) {
            const row = document.createElement("tr");

            addAdminCell(row, session.sessionId);
            addAdminCell(row, session.userId);
            addAdminCell(row, session.status);
            addAdminCell(row, session.startTime);
            addAdminCell(row, session.endTime);

            const actionsCell = document.createElement("td");
            actionsCell.style.padding = "10px";

            if (session.status === "ACTIVE") {
                const button = createAdminButton(
                    "Force Cleanup",
                    "#dc3545",
                    function () {
                        forceCleanupSession(session.sessionId);
                    }
                );

                actionsCell.appendChild(button);
            } else {
                actionsCell.textContent = "No action required";
            }

            row.appendChild(actionsCell);
            tbody.appendChild(row);
        });

        wrapper.appendChild(table);
        sessionList.replaceChildren(wrapper);

    } catch (error) {
        console.error("LOAD SESSIONS ERROR:", error);
        sessionList.textContent = "Error loading sessions.";
    }
}


/* =========================================================
   FORCE-CLEANUP SESSION
   ========================================================= */

async function forceCleanupSession(sessionId) {
    if (!sessionId) {
        showAdminMessage("Invalid session ID.", true);
        return;
    }

    if (!window.confirm(
        `Terminate session ${sessionId} and clean up its workspace?`
    )) {
        return;
    }

    try {
        const response = await fetch(
            `/api/admin/sessions/${encodeURIComponent(sessionId)}/force-cleanup`,
            {
                method: "PUT",
                credentials: "same-origin"
            }
        );

        const data = await response.json().catch(() => ({}));

        if (!response.ok) {
            throw new Error(
                data.message || "Session cleanup failed."
            );
        }

        if (
            data.status === "ALREADY_COMPLETED"
        ) {
            showAdminMessage(
                data.message || "Session is no longer active.",
                true
            );
        } else {
            showAdminMessage(
                data.message || "Session cleanup completed."
            );
        }

        await loadSessions();

    } catch (error) {
        console.error("FORCE CLEANUP ERROR:", error);
        showAdminMessage(error.message, true);
    }
}


/* =========================================================
   LOAD AUDIT LOGS
   Requires backend endpoint:
   GET /api/admin/audit-logs
   ========================================================= */

async function loadAuditLogs() {
    const auditLogList =
        document.getElementById("auditLogList");

    if (!auditLogList) {
        return;
    }

    auditLogList.textContent = "Loading audit logs...";

    try {
        const response = await fetch("/api/admin/audit-logs", {
            method: "GET",
            credentials: "same-origin"
        });

        if (!response.ok) {
            auditLogList.textContent =
                response.status === 401 || response.status === 403
                    ? "Admin authentication required."
                    : response.status === 404
                        ? "Audit log API is not implemented yet."
                        : "Unable to load audit logs.";

            return;
        }

        const logs = await response.json();

        if (!Array.isArray(logs) || logs.length === 0) {
            auditLogList.textContent = "No audit logs found.";
            return;
        }

        const wrapper = document.createElement("div");
        wrapper.style.overflowX = "auto";

        const { table, tbody } = createAdminTable([
            "Timestamp",
            "User ID",
            "Action",
            "Details"
        ]);

        logs.forEach(function (log) {
            const row = document.createElement("tr");

            // Supports common property names. Match these to
            // the fields returned by your audit-log backend.
            addAdminCell(
                row,
                log.timestamp ?? log.createdAt ?? log.eventTime
            );

            addAdminCell(
                row,
                log.userId ?? log.actorUserId
            );

            addAdminCell(
                row,
                log.action ?? log.eventType
            );

            addAdminCell(
                row,
                log.details ?? log.description ?? log.message
            );

            tbody.appendChild(row);
        });

        wrapper.appendChild(table);
        auditLogList.replaceChildren(wrapper);

    } catch (error) {
        console.error("LOAD AUDIT LOGS ERROR:", error);
        auditLogList.textContent = "Error loading audit logs.";
    }
}


/* =========================================================
   ADMIN LOGOUT
   ========================================================= */

async function logoutAdmin() {
    try {
        const response = await fetch("/api/admin/logout", {
            method: "POST",
            credentials: "same-origin"
        });

        console.log("ADMIN LOGOUT STATUS:", response.status);

    } catch (error) {
        console.error("ADMIN LOGOUT ERROR:", error);

    } finally {
        window.location.href = "/admin.html";
    }
}
