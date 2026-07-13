import { test as setup, expect } from '@playwright/test'

const AUTH_FILE = 'e2e/.auth/user.json'
const TEST_EMAIL = `e2e-${Date.now()}@example.com`
const TEST_PASSWORD = 'StrongPass123!'

interface MailpitMessage {
  ID: string
  Text: string
  HTML: string
}

interface MailpitMessagesResponse {
  messages: MailpitMessage[]
}

interface LoginResponse {
  accessToken: string
}

setup('authenticate', async ({ page, baseURL }) => {
  const apiBase = baseURL ?? 'http://localhost:5173'
  const mailpitUrl = process.env.MAILPIT_URL ?? 'http://localhost:8025'

  // 1. Sign up
  const signupRes = await page.request.post(`${apiBase}/api/v1/auth/signup`, {
    data: { email: TEST_EMAIL, password: TEST_PASSWORD },
  })
  expect(signupRes.ok()).toBeTruthy()

  // 2. Poll Mailpit's search API for the verification email addressed to this recipient. Filtering
  //    by recipient avoids picking up a pre-existing message; polling replaces a fixed sleep so the
  //    async dispatch is awaited exactly as long as it takes.
  const searchUrl = `${mailpitUrl}/api/v1/search?query=${encodeURIComponent(`to:${TEST_EMAIL}`)}`
  await expect
    .poll(
      async () => {
        const res = await page.request.get(searchUrl)
        if (!res.ok()) {
          return 0
        }
        const data = (await res.json()) as MailpitMessagesResponse
        return data.messages.length
      },
      { message: `verification email for ${TEST_EMAIL}`, timeout: 15_000 },
    )
    .toBeGreaterThan(0)

  // 3. Fetch the newest matching message (Mailpit returns newest-first) and extract the token.
  const searchRes = await page.request.get(searchUrl)
  const searchData = (await searchRes.json()) as MailpitMessagesResponse
  const messageId = searchData.messages[0].ID
  const msgRes = await page.request.get(`${mailpitUrl}/api/v1/message/${messageId}`)
  expect(msgRes.ok()).toBeTruthy()
  const msg = (await msgRes.json()) as MailpitMessage

  const body: string = msg.Text || msg.HTML
  const tokenMatch = body.match(/[?&]token=([^\s&"<]+)/)
  expect(tokenMatch).not.toBeNull()
  const verificationToken = tokenMatch![1]

  // 4. Verify email
  const verifyRes = await page.request.post(`${apiBase}/api/v1/auth/verify`, {
    data: { token: verificationToken },
  })
  expect(verifyRes.ok()).toBeTruthy()

  // 5. Login
  const loginRes = await page.request.post(`${apiBase}/api/v1/auth/login`, {
    data: { email: TEST_EMAIL, password: TEST_PASSWORD },
  })
  expect(loginRes.ok()).toBeTruthy()
  const loginBody = (await loginRes.json()) as LoginResponse
  const accessToken = loginBody.accessToken
  expect(accessToken).toBeTruthy()

  // 6. Navigate to app and inject token into localStorage
  await page.goto(apiBase)
  await page.evaluate((token) => {
    localStorage.setItem('accessToken', token)
  }, accessToken)

  // 7. Save storage state (includes localStorage)
  await page.context().storageState({ path: AUTH_FILE })
})
