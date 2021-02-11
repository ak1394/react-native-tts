import { driver, By2 } from 'selenium-appium'

const setup = require('../jest-setups/jest.setup');
jest.setTimeout(50000);

beforeAll(() => {
  return driver.startWithCapabilities(setup.capabilites);
});

afterAll(() => {
  return driver.quit();
});

describe('Test App', () => {

  test('Initializes TTS', async () => {
    await driver.sleep(500);
    let status = await By2.nativeAccessibilityId("Status").getText();
    expect(status).toBe("Status: initialized")
  });

})
