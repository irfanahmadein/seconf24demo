package steps;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.AfterStep;
import io.cucumber.java.Scenario;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.CapabilityType;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.zaproxy.clientapi.core.ClientApi;
import org.zaproxy.clientapi.core.ClientApiException;
import org.zaproxy.clientapi.core.ApiResponse;
import org.zaproxy.clientapi.core.ApiResponseElement;
import pages.JuiceShopPage;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;

public class Hooks {

    public static WebDriver driver;
    private static final String ZAP_ADDRESS = "localhost";
    private static final int ZAP_PORT = 8090;
    private static final String ZAP_API_KEY = "changeme"; // Change this to your actual ZAP API key
    private static ClientApi api = new ClientApi(ZAP_ADDRESS, ZAP_PORT, ZAP_API_KEY);
    private String scanType = System.getProperty("scan");
    private boolean isHeadless = Boolean.parseBoolean(System.getProperty("headless", "true"));
    private JuiceShopPage juiceShopPage;

    @Before
    public void setUp() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();

        if (!"false".equals(scanType)) {
            Proxy proxy = new Proxy();
            proxy.setHttpProxy(ZAP_ADDRESS + ":" + ZAP_PORT);
            proxy.setSslProxy(ZAP_ADDRESS + ":" + ZAP_PORT);
            options.setCapability(CapabilityType.PROXY, proxy);
        }

        if (isHeadless) {
            options.addArguments("--headless");
        }

        driver = new ChromeDriver(options);
        juiceShopPage = new JuiceShopPage(driver);

        if ("active".equals(scanType)) {
            try {
                api.core.newSession("", "", "");
            } catch (ClientApiException e) {
                e.printStackTrace();
            }
        }
    }

    @After
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }

        if ("passive".equals(scanType)) {
            generateZapReport("passive-scan-report.html");
        } else if ("active".equals(scanType)) {
            performActiveScan(juiceShopPage.getTargetUrl());
            generateZapReport("active-scan-report.html");
        }
    }

    @AfterStep
    public void afterStep(Scenario scenario) {
        String currentUrl = driver.getCurrentUrl();

        try {
            if ("passive".equals(scanType)) {
                // For passive scan, we do not wait
                api.pscan.enableAllScanners();
                api.pscan.setEnabled("true");
            } else if ("active".equals(scanType)) {
                // For active scan, we wait for the scan to complete
                performActiveScan(currentUrl);
            }
        } catch (ClientApiException e) {
            e.printStackTrace();
        }
    }

    private void performActiveScan(String targetUrl) {
        try {
            ApiResponse resp = api.ascan.scan(targetUrl, "True", "False", null, null, null);

            // Scan response returns scan id to support concurrent scanning.
            String scanId = ((ApiResponseElement) resp).getValue();

            // Poll the status until the scan is complete
            int progress;
            do {
                progress = Integer.parseInt(((ApiResponseElement) api.ascan.status(scanId)).getValue());
                System.out.println("Scan progress : " + progress + "%");
                Thread.sleep(1000);
            } while (progress < 100);
        } catch (ClientApiException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void generateZapReport(String fileName) {
        try {
            byte[] report = api.core.htmlreport();
            Files.write(Paths.get(fileName), report);
        } catch (ClientApiException | IOException e) {
            e.printStackTrace();
        }
    }
}
