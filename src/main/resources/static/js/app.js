console.log("APP.JS LOADED SUCCESSFULLY");


// ======================================================
// SHOW LOGIN
// ======================================================

function showLogin() {

    document
        .getElementById("loginSection")
        .classList
        .remove("hidden");

    document
        .getElementById("registerSection")
        .classList
        .add("hidden");
}


// ======================================================
// SHOW REGISTER
// ======================================================

function showRegister() {

    document
        .getElementById("loginSection")
        .classList
        .add("hidden");

    document
        .getElementById("registerSection")
        .classList
        .remove("hidden");
}


// ======================================================
// LOGIN USER
// ======================================================

async function loginUser() {

    console.log("LOGIN BUTTON CLICKED");

    const username =
        document.getElementById("loginUsername").value.trim();

    const password =
        document.getElementById("loginPassword").value;

    const message =
        document.getElementById("loginMessage");

    if (!username || !password) {

        message.innerText =
            "Please enter username and password.";

        return;
    }

    try {

        const response =
            await fetch("/api/users/login", {

                method: "POST",

                headers: {
                    "Content-Type": "application/json"
                },

                credentials: "include",

                body: JSON.stringify({
                    username: username,
                    password: password
                })
            });

        const data =
            await response.json();

        console.log(
            "LOGIN RESPONSE:",
            data
        );

        if (!response.ok) {

            message.innerText =
                data.message ||
                "Login failed.";

            return;
        }

        message.innerText =
            "Login successful!";

        localStorage.setItem(
            "username",
            data.username
        );

        window.location.href =
            "/dashboard.html";

    } catch (error) {

        console.error(
            "LOGIN ERROR:",
            error
        );

        message.innerText =
            "Server connection error.";
    }
}


// ======================================================
// REGISTER USER
// ======================================================

async function registerUser() {

    console.log(
        "REGISTER BUTTON CLICKED"
    );

    const username =
        document.getElementById(
            "registerUsername"
        ).value.trim();

    const password =
        document.getElementById(
            "registerPassword"
        ).value;

    const message =
        document.getElementById(
            "registerMessage"
        );

    if (!username || !password) {

        message.innerText =
            "Please enter username and password.";

        return;
    }

    if (username.length < 3) {

        message.innerText =
            "Username must be at least 3 characters.";

        return;
    }

    if (password.length < 8) {

        message.innerText =
            "Password must be at least 8 characters.";

        return;
    }

    try {

        const response =
            await fetch(
                "/api/users/register",
                {

                    method: "POST",

                    headers: {
                        "Content-Type":
                            "application/json"
                    },

                    body: JSON.stringify({

                        username: username,

                        password: password
                    })
                }
            );

        const text =
            await response.text();

        console.log(
            "REGISTER RESPONSE:",
            text
        );

        if (!response.ok) {

            message.innerText =
                text ||
                "Registration failed.";

            return;
        }

        message.innerText =
            "Registration successful! Please login.";

        setTimeout(
            showLogin,
            1500
        );

    } catch (error) {

        console.error(
            "REGISTER ERROR:",
            error
        );

        message.innerText =
            "Server connection error.";
    }
}


// ======================================================
// START SECURE SESSION
// ======================================================

async function createWorkspace() {

    console.log(
        "START SECURE SESSION BUTTON CLICKED"
    );

    const message =
        document.getElementById(
            "workspaceMessage"
        ) ||
        document.getElementById(
            "dashboardMessage"
        );

    try {

        const response =
            await fetch(
                "/api/session/start",
                {
                    method: "POST",
                    credentials: "include"
                }
            );

        const data =
            await response.json();

        console.log(
            "SESSION START RESPONSE:",
            data
        );

        if (!response.ok) {

            if (message) {

                message.innerText =
                    data.message ||
                    "Unable to start secure session.";
            }

            if (data.status === "NOT_LOGGED_IN") {

                setTimeout(() => {

                    window.location.href =
                        "/index.html";

                }, 1000);
            }

            return;
        }

        if (
            data.status ===
            "SESSION_STARTED"
        ) {

            if (message) {

                message.innerText =
                    "Secure session started successfully.";
            }

            setTimeout(() => {

                window.location.href =
                    "/workspace.html";

            }, 500);

            return;
        }

        if (
            data.status ===
            "SESSION_ALREADY_ACTIVE"
        ) {

            if (message) {

                message.innerText =
                    "A secure session is already active.";
            }

            setTimeout(() => {

                window.location.href =
                    "/workspace.html";

            }, 500);

            return;
        }

        if (
            data.status ===
            "NOT_LOGGED_IN"
        ) {

            if (message) {

                message.innerText =
                    "Please login first.";
            }

            return;
        }

        if (message) {

            message.innerText =
                data.message ||
                "Unable to start secure session.";
        }

    } catch (error) {

        console.error(
            "SESSION START ERROR:",
            error
        );

        if (message) {

            message.innerText =
                "Server connection error.";
        }
    }
}


// ======================================================
// END SECURE SESSION
// ======================================================

async function endSecureSession() {

    try {

        const response =
            await fetch(
                "/api/session/my-sessions",
                {
                    method: "GET",
                    credentials: "include"
                }
            );

        if (!response.ok) {

            alert(
                "Unable to find active session."
            );

            return;
        }

        const sessions =
            await response.json();

        if (!Array.isArray(sessions)) {

            alert(
                "Invalid session response."
            );

            return;
        }

        const activeSession =
            sessions.find(
                session =>
                    session.status === "ACTIVE"
            );

        if (!activeSession) {

            alert(
                "No active secure session found."
            );

            return;
        }

        const confirmed =
            confirm(
                "Are you sure you want to end the secure session?"
            );

        if (!confirmed) {
            return;
        }

        const sessionId =
            activeSession.sessionId;

        const endResponse =
            await fetch(
                "/api/session/end?sessionId="
                + encodeURIComponent(sessionId),
                {
                    method: "POST",
                    credentials: "include"
                }
            );

        const data =
            await endResponse.json();

        if (!endResponse.ok) {

            alert(
                data.message ||
                "Unable to end secure session."
            );

            return;
        }

        if (
            data.status ===
            "SESSION_ENDED"
        ) {

            alert(
                "Secure session ended successfully."
            );

            window.location.href =
                "/dashboard.html";

            return;
        }

        alert(
            data.message ||
            "Unable to end secure session."
        );

    } catch (error) {

        console.error(
            "END SESSION ERROR:",
            error
        );

        alert(
            "Server connection error."
        );
    }
}


// ======================================================
// OPEN SECURE WORKSPACE
// ======================================================

// ======================================================
// OPEN SECURE WORKSPACE
// ======================================================

async function openWorkspace() {

    try {

        const response =
            await fetch(
                "/api/workspace/open",
                {
                    method: "POST",
                    credentials: "include"
                }
            );

        const message =
            await response.text();

        console.log(
            "OPEN WORKSPACE RESPONSE:",
            message
        );

        if (!response.ok) {

            alert(
                message ||
                "Unable to open secure workspace."
            );

            return;
        }

        alert(
            message ||
            "Secure workspace opened."
        );

    } catch (error) {

        console.error(
            "OPEN WORKSPACE ERROR:",
            error
        );

        alert(
            "Unable to connect to server."
        );
    }
}

// ======================================================
// VIEW SESSION HISTORY
// ======================================================

async function viewSessions() {

    const history =
        document.getElementById(
            "sessionHistory"
        );

    if (!history) {
        return;
    }

    history.classList.remove(
        "hidden"
    );

    history.innerHTML =
        "<p>Loading session history...</p>";

    try {

        const response =
            await fetch(
                "/api/session/my-sessions",
                {
                    method: "GET",
                    credentials: "include"
                }
            );

        const data =
            await response.json();

        if (!response.ok) {

            history.innerHTML =
                "<p>"
                + (
                    data.message ||
                    "Unable to load sessions."
                )
                + "</p>";

            return;
        }

        if (!Array.isArray(data)
                || data.length === 0) {

            history.innerHTML =
                "<p>No session history found.</p>";

            return;
        }

        const table =
            document.createElement(
                "table"
            );

        table.style.width =
            "100%";

        table.style.borderCollapse =
            "collapse";

        const thead =
            document.createElement(
                "thead"
            );

        const headerRow =
            document.createElement(
                "tr"
            );

        [
            "Session ID",
            "Status",
            "Start Time",
            "End Time"
        ].forEach(
            heading => {

                const th =
                    document.createElement(
                        "th"
                    );

                th.textContent =
                    heading;

                headerRow.appendChild(
                    th
                );
            }
        );

        thead.appendChild(
            headerRow
        );

        const tbody =
            document.createElement(
                "tbody"
            );

        data.forEach(
            session => {

                const row =
                    document.createElement(
                        "tr"
                    );

                const sessionId =
                    document.createElement(
                        "td"
                    );

                sessionId.textContent =
                    session.sessionId ?? "";

                const status =
                    document.createElement(
                        "td"
                    );

                status.textContent =
                    session.status ?? "";

                const start =
                    document.createElement(
                        "td"
                    );

                start.textContent =
                    session.startTime ?? "";

                const end =
                    document.createElement(
                        "td"
                    );

                end.textContent =
                    session.endTime ?? "";

                row.appendChild(
                    sessionId
                );

                row.appendChild(
                    status
                );

                row.appendChild(
                    start
                );

                row.appendChild(
                    end
                );

                tbody.appendChild(
                    row
                );
            }
        );

        table.appendChild(
            thead
        );

        table.appendChild(
            tbody
        );

        history.innerHTML = "";

        history.appendChild(
            table
        );

    } catch (error) {

        console.error(
            "SESSION HISTORY ERROR:",
            error
        );

        history.innerHTML =
            "<p>Error loading session history.</p>";
    }
}


// ======================================================
// SHOW RESET PASSWORD
// ======================================================

function showResetPassword() {

    const section =
        document.getElementById(
            "resetPasswordSection"
        );

    if (!section) {
        return;
    }

    section.classList.toggle(
        "hidden"
    );
}


// ======================================================
// RESET PASSWORD
// ======================================================

async function resetPassword() {

    const passwordInput =
        document.getElementById(
            "newPassword"
        );

    const message =
        document.getElementById(
            "resetMessage"
        );

    if (!passwordInput || !message) {
        return;
    }

    const newPassword =
        passwordInput.value;

    if (!newPassword) {

        message.innerText =
            "Please enter a new password.";

        return;
    }

    if (newPassword.length < 8) {

        message.innerText =
            "Password must be at least 8 characters.";

        return;
    }

    try {

        const response =
            await fetch(
                "/api/users/reset-password?newPassword="
                + encodeURIComponent(newPassword),
                {
                    method: "POST",
                    credentials: "include"
                }
            );

        const data =
            await response.json();

        if (!response.ok) {

            message.innerText =
                data.message ||
                "Unable to reset password.";

            return;
        }

        message.innerText =
            data.message ||
            "Password changed successfully.";

        passwordInput.value = "";

    } catch (error) {

        console.error(
            "RESET PASSWORD ERROR:",
            error
        );

        message.innerText =
            "Server connection error.";
    }
}


// ======================================================
// LOGOUT USER
// ======================================================

async function logoutUser() {

    console.log(
        "LOGOUT BUTTON CLICKED"
    );

    try {

        const response =
            await fetch(
                "/api/users/logout",
                {
                    method: "POST",
                    credentials: "include"
                }
            );

        const data =
            await response.json();

        console.log(
            "LOGOUT RESPONSE:",
            data
        );

        if (
            data.status ===
            "LOGOUT_FAILED"
        ) {

            alert(
                data.message ||
                "Logout failed because workspace cleanup failed."
            );

            return;
        }

        if (
            data.status ===
                "LOGOUT_SUCCESSFUL"
            ||
            data.status ===
                "NOT_LOGGED_IN"
        ) {

            localStorage.removeItem(
                "username"
            );

            window.location.href =
                "/index.html";

            return;
        }

        alert(
            data.message ||
            "Unable to logout."
        );

    } catch (error) {

        console.error(
            "LOGOUT ERROR:",
            error
        );

        alert(
            "Server connection error. "
            + "Logout could not be completed."
        );
    }
}

// =========================================================
// SHOW / HIDE REGISTER PASSWORD
// =========================================================

function toggleRegisterPassword() {

    const passwordInput =
        document.getElementById("registerPassword");

    const toggleButton =
        document.querySelector(".password-toggle");

    if (!passwordInput || !toggleButton) {
        return;
    }

    if (passwordInput.type === "password") {

        passwordInput.type = "text";

        toggleButton.innerText = "🙈";

        toggleButton.setAttribute(
            "aria-label",
            "Hide password"
        );

    } else {

        passwordInput.type = "password";

        toggleButton.innerText = "👁";

        toggleButton.setAttribute(
            "aria-label",
            "Show password"
        );
    }
}


// =========================================================
// CHECK PASSWORD REQUIREMENTS
// =========================================================

function checkPasswordStrength() {

    const passwordInput =
        document.getElementById("registerPassword");

    if (!passwordInput) {
        return;
    }

    const password =
        passwordInput.value;


    // 8–30 characters
    updatePasswordRequirement(
        "passwordLength",
        password.length >= 8 &&
        password.length <= 30
    );


    // Uppercase
    updatePasswordRequirement(
        "passwordUpper",
        /[A-Z]/.test(password)
    );


    // Lowercase
    updatePasswordRequirement(
        "passwordLower",
        /[a-z]/.test(password)
    );


    // Number
    updatePasswordRequirement(
        "passwordNumber",
        /\d/.test(password)
    );


    // Special character
    updatePasswordRequirement(
        "passwordSpecial",
        /[^A-Za-z0-9\s]/.test(password)
    );


    // No spaces / whitespace
    updatePasswordRequirement(
        "passwordSpace",
        !/\s/.test(password)
    );
}


// =========================================================
// UPDATE PASSWORD REQUIREMENT
// =========================================================

function updatePasswordRequirement(
    elementId,
    isValid
) {

    const element =
        document.getElementById(elementId);

    if (!element) {
        return;
    }

    const currentText =
        element.innerText
            .replace(/^✓ /, "")
            .replace(/^✗ /, "");

    if (isValid) {

        element.innerText =
            "✓ " + currentText;

        element.classList.add("valid");

        element.classList.remove("invalid");

    } else {

        element.innerText =
            "✗ " + currentText;

        element.classList.add("invalid");

        element.classList.remove("valid");
    }
}