// =========================================================
// ADMIN LOGIN
// =========================================================

async function adminLogin() {

    const username =
        document.getElementById(
            "adminUsername"
        ).value.trim();

    const password =
        document.getElementById(
            "adminPassword"
        ).value;

    const message =
        document.getElementById(
            "adminLoginMessage"
        );

    if (!username || !password) {

        message.textContent =
            "Please enter username and password.";

        return;
    }

    if (password.length < 8) {

        message.textContent =
            "Password must be at least 8 characters.";

        return;
    }

    try {

        const response =
            await fetch(
                "/api/admin/login",
                {
                    method: "POST",

                    headers: {
                        "Content-Type":
                            "application/json"
                    },

                    credentials: "include",

                    body: JSON.stringify({
                        username: username,
                        password: password
                    })
                }
            );

        const data =
            await response.json();

        if (!response.ok) {

            message.textContent =
                data.message ||
                "Admin login failed.";

            return;
        }

        if (data.role !== "ADMIN") {

            message.textContent =
                "Access denied. Admin privileges required.";

            return;
        }

        window.location.href =
            "/admin-dashboard.html";

    } catch (error) {

        console.error(
            "ADMIN LOGIN ERROR:",
            error
        );

        message.textContent =
            "Unable to connect to server.";
    }
}


// =========================================================
// BACK TO USER LOGIN
// =========================================================

function goToUserLogin() {

    window.location.href =
        "/index.html";
}


// =========================================================
// LOAD USERS
// =========================================================

async function loadUsers() {

    const userList =
        document.getElementById(
            "userList"
        );

    if (!userList) {
        return;
    }

    userList.innerHTML =
        "<p>Loading users...</p>";

    try {

        const response =
            await fetch(
                "/api/admin/users",
                {
                    method: "GET",
                    credentials: "include"
                }
            );

        if (!response.ok) {

            if (response.status === 401
                    || response.status === 403) {

                userList.innerHTML =
                    "<p>Admin authentication required.</p>";

            } else {

                userList.innerHTML =
                    "<p>Unable to load users.</p>";
            }

            return;
        }

        const users =
            await response.json();

        if (!Array.isArray(users)
                || users.length === 0) {

            userList.innerHTML =
                "<p>No users found.</p>";

            return;
        }

        const wrapper =
            document.createElement("div");

        wrapper.style.overflowX =
            "auto";

        const table =
            document.createElement("table");

        table.style.width =
            "100%";

        table.style.borderCollapse =
            "collapse";

        // -----------------------------------------------------
        // TABLE HEADER
        // -----------------------------------------------------

        const thead =
            document.createElement("thead");

        const headerRow =
            document.createElement("tr");

        [
            "ID",
            "Username",
            "Role",
            "Status"
        ].forEach(
            function (heading) {

                const th =
                    document.createElement("th");

                th.textContent =
                    heading;

                headerRow.appendChild(th);
            }
        );

        thead.appendChild(
            headerRow
        );

        // -----------------------------------------------------
        // TABLE BODY
        // -----------------------------------------------------

        const tbody =
            document.createElement("tbody");

        users.forEach(
            function (user) {

                const row =
                    document.createElement("tr");

                const idCell =
                    document.createElement("td");

                idCell.textContent =
                    user.userId ?? "";

                const usernameCell =
                    document.createElement("td");

                usernameCell.textContent =
                    user.username ?? "";

                const roleCell =
                    document.createElement("td");

                roleCell.textContent =
                    user.role ?? "";

                const statusCell =
                    document.createElement("td");

                statusCell.textContent =
                    user.status ?? "";

                row.appendChild(idCell);
                row.appendChild(usernameCell);
                row.appendChild(roleCell);
                row.appendChild(statusCell);

                tbody.appendChild(row);
            }
        );

        table.appendChild(thead);
        table.appendChild(tbody);

        wrapper.appendChild(table);

        userList.innerHTML = "";
        userList.appendChild(wrapper);

    } catch (error) {

        console.error(
            "LOAD USERS ERROR:",
            error
        );

        userList.innerHTML =
            "<p>Error loading users.</p>";
    }
}


// =========================================================
// LOAD SESSIONS
// =========================================================

async function loadSessions() {

    const sessionList =
        document.getElementById(
            "sessionList"
        );

    if (!sessionList) {
        return;
    }

    sessionList.innerHTML =
        "<p>Loading sessions...</p>";

    try {

        const response =
            await fetch(
                "/api/admin/sessions",
                {
                    method: "GET",
                    credentials: "include"
                }
            );

        if (!response.ok) {

            if (response.status === 401
                    || response.status === 403) {

                sessionList.innerHTML =
                    "<p>Admin authentication required.</p>";

            } else {

                sessionList.innerHTML =
                    "<p>Unable to load sessions.</p>";
            }

            return;
        }

        const sessions =
            await response.json();

        if (!Array.isArray(sessions)
                || sessions.length === 0) {

            sessionList.innerHTML =
                "<p>No sessions found.</p>";

            return;
        }

        const wrapper =
            document.createElement("div");

        wrapper.style.overflowX =
            "auto";

        const table =
            document.createElement("table");

        table.style.width =
            "100%";

        table.style.borderCollapse =
            "collapse";

        // -----------------------------------------------------
        // TABLE HEADER
        // -----------------------------------------------------

        const thead =
            document.createElement("thead");

        const headerRow =
            document.createElement("tr");

        [
            "Session ID",
            "User ID",
            "Status",
            "Start Time",
            "End Time"
        ].forEach(
            function (heading) {

                const th =
                    document.createElement("th");

                th.textContent =
                    heading;

                headerRow.appendChild(th);
            }
        );

        thead.appendChild(
            headerRow
        );

        // -----------------------------------------------------
        // TABLE BODY
        // -----------------------------------------------------

        const tbody =
            document.createElement("tbody");

        sessions.forEach(
            function (session) {

                const row =
                    document.createElement("tr");

                const sessionIdCell =
                    document.createElement("td");

                sessionIdCell.textContent =
                    session.sessionId ?? "";

                const userIdCell =
                    document.createElement("td");

                userIdCell.textContent =
                    session.userId ?? "";

                const statusCell =
                    document.createElement("td");

                statusCell.textContent =
                    session.status ?? "";

                const startCell =
                    document.createElement("td");

                startCell.textContent =
                    session.startTime ?? "";

                const endCell =
                    document.createElement("td");

                endCell.textContent =
                    session.endTime ?? "";

                row.appendChild(
                    sessionIdCell
                );

                row.appendChild(
                    userIdCell
                );

                row.appendChild(
                    statusCell
                );

                row.appendChild(
                    startCell
                );

                row.appendChild(
                    endCell
                );

                tbody.appendChild(row);
            }
        );

        table.appendChild(thead);
        table.appendChild(tbody);

        wrapper.appendChild(table);

        sessionList.innerHTML = "";
        sessionList.appendChild(wrapper);

    } catch (error) {

        console.error(
            "LOAD SESSIONS ERROR:",
            error
        );

        sessionList.innerHTML =
            "<p>Error loading sessions.</p>";
    }
}


// =========================================================
// ADMIN LOGOUT
// =========================================================

async function logoutAdmin() {

    try {

        const response =
            await fetch(
                "/api/admin/logout",
                {
                    method: "POST",
                    credentials: "include"
                }
            );

        console.log(
            "ADMIN LOGOUT STATUS:",
            response.status
        );

        // Regardless of the response,
        // return to admin login.
        window.location.href =
            "/admin.html";

    } catch (error) {

        console.error(
            "ADMIN LOGOUT ERROR:",
            error
        );

        window.location.href =
            "/admin.html";
    }
}