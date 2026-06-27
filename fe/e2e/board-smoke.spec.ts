import { test, expect } from '@playwright/test'

test.describe('@smoke Board', () => {
  test('boardPage_shouldRenderFiveColumns_whenStackIsRunning', async ({ page }) => {
    await page.goto('/')

    const board = page.getByTestId('board')
    await expect(board).toBeVisible({ timeout: 30_000 })

    const columns = page.getByTestId('board-column')
    await expect(columns).toHaveCount(5)
  })
})
