import { test, expect, type Locator } from '@playwright/test'

interface Team {
  id: string
}

/** Bounding box of a locator, failing loudly if it isn't laid out. */
async function boxOf(locator: Locator) {
  const box = await locator.boundingBox()
  if (!box) {
    throw new Error('element has no bounding box')
  }
  return box
}

test.describe('@smoke Board', () => {
  test('board_shouldPersistDraggedCardAcrossReload_whenStackIsRunning', async ({
    page,
    baseURL,
  }) => {
    const apiBase = baseURL ?? 'http://localhost:5173'

    // Read the token seeded by the auth setup so API calls carry the same session.
    await page.goto('/')
    const token = await page.evaluate(() => localStorage.getItem('accessToken'))
    expect(token).toBeTruthy()
    const headers = { Authorization: `Bearer ${token}` }
    const stamp = Date.now()

    // Seed a team and a "new"-state ticket via the API.
    const teamRes = await page.request.post(`${apiBase}/api/v1/teams`, {
      headers,
      data: { name: `Board smoke ${stamp}` },
    })
    expect(teamRes.ok()).toBeTruthy()
    const team = (await teamRes.json()) as Team

    const cardTitle = `Drag me ${stamp}`
    const ticketRes = await page.request.post(`${apiBase}/api/v1/tickets`, {
      headers,
      data: { teamId: team.id, type: 'bug', state: 'new', title: cardTitle, body: 'body' },
    })
    expect(ticketRes.ok()).toBeTruthy()

    // Open the board for that team; the card starts in the "New" column.
    await page.goto(`/?teamId=${team.id}`)
    const newColumn = page.getByRole('region', { name: 'New' })
    const inProgressColumn = page.getByRole('region', { name: 'In progress' })
    await expect(newColumn.getByText(cardTitle)).toBeVisible({ timeout: 30_000 })

    // Drag the card from "New" to "In progress". dnd-kit's 8px activation needs a real move first,
    // so nudge past the threshold before travelling to the target column.
    const card = page.getByTestId('ticket-card').filter({ hasText: cardTitle })
    const from = await boxOf(card)
    const to = await boxOf(inProgressColumn)
    await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2)
    await page.mouse.down()
    await page.mouse.move(from.x + from.width / 2 + 20, from.y + from.height / 2, { steps: 5 })
    await page.mouse.move(to.x + to.width / 2, to.y + to.height / 2, { steps: 10 })
    await page.mouse.up()

    await expect(inProgressColumn.getByText(cardTitle)).toBeVisible()

    // Reload: the persisted state must keep the card in "In progress".
    await page.goto(`/?teamId=${team.id}`)
    await expect(inProgressColumn.getByText(cardTitle)).toBeVisible({ timeout: 30_000 })
    await expect(newColumn.getByText(cardTitle)).toHaveCount(0)
  })
})
