package org.example;

import com.github.javafaker.Faker;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class OpenCartTest {
    private WebDriver driver;
    private WebDriverWait wait;
    private Properties properties;

    @BeforeClass
    public void setUp() {
        WebDriverManager.chromedriver().setup();
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("prefs", prefs);
        options.addArguments("--ignore-certificate-errors", "--disable-popup-blocking", "--disable-notifications", "--disable-infobars", "--incognito");
        driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        properties = new Properties();
        try (FileInputStream file = new FileInputStream("src/test/resources/credentials.properties")) {
            properties.load(file);
        } catch (IOException e) {
            System.out.println("ERROR: No se pudo cargar el archivo credentials.properties.");
            System.exit(1);
        }
    }

    private void takeScreenshot(String testName) {
        try {
            File srcFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File destFile = new File("screenshots/" + testName + "_" + timestamp + ".png");
            destFile.getParentFile().mkdirs();
            org.apache.commons.io.FileUtils.copyFile(srcFile, destFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public  String getDateNextDay() {
        LocalDate manana = LocalDate.now().plusDays(1);
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return manana.format(formato);
    }

    private void clickElement(By locator) {
        wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
    }

    private void fillInput(By locator, String value) {
        WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
        element.clear();
        element.sendKeys(value);
    }

    private void addProductToCart(String productName) {
        fillInput(By.name("search"), productName);
        clickElement(By.xpath("//button[contains(@class, 'btn-default')]"));
        clickElement(By.linkText(productName));
        clickElement(By.id("button-cart"));
        takeScreenshot("Add_" + productName.replace(" ", "_"));
    }

    private void addAdditionalProductToCart(String productName) {
        fillInput(By.name("search"), productName);
        clickElement(By.xpath("//button[contains(@class, 'btn-default')]"));
        clickElement(By.xpath("//button[contains(@onclick,'cart.add')]"));
        takeScreenshot("Add_Additional_" + productName.replace(" ", "_"));
    }

    private void addProductToCompare(String productName) {
        fillInput(By.name("search"), productName);
        clickElement(By.xpath("//button[contains(@class, 'btn-default')]"));
        clickElement(By.xpath("//button[@data-original-title='Compare this Product']"));
        takeScreenshot("Add_Compare" + productName.replace(" ", "_"));
    }

    public void logout() {
        System.out.println("Realizando LogOut");
        clickElement(By.xpath("//span[contains(text(), 'My Account')]"));
        clickElement(By.xpath("(//a[contains(text(), 'Logout')])[1]"));
    }

    @Test(priority = 1)
    public void addToCartTest() {
        driver.get("http://opencart.abstracta.us/index.php?route=common/home");
        addProductToCart("iPod Classic");
        addProductToCart("iMac");
    }

    @Test(priority = 2)
    public void openViewCart() {
        WebElement cartButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("cart-total")));

        // Manejo de StaleElementReferenceException en el botón del carrito
        for (int i = 0; i < 3; i++) {
            try {
                cartButton.click();
                break;
            } catch (StaleElementReferenceException e) {
                System.out.println("El botón del carrito se actualizó, volviendo a buscarlo...");
                cartButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("cart-total")));
            }
        }

        clickElement(By.xpath("//a[contains(@href, 'route=checkout/cart')]"));
        wait.until(ExpectedConditions.urlContains("route=checkout/cart"));
        takeScreenshot("Cart_Page_Loaded");

        // Esperar que la tabla del carrito esté presente antes de continuar
        WebElement cartTable = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//table[contains(@class, 'table')]")));

        for (String product : new String[]{"iMac", "iPod Classic"}) {
            boolean productFound = false;

            // Intentar encontrar el producto con reintento en caso de StaleElementReferenceException
            for (int i = 0; i < 3; i++) {
                try {
                    WebElement productElement = wait.until(ExpectedConditions.presenceOfElementLocated(
                            By.xpath("(//table[contains(@class, 'table')]//td[@class='text-left']/a[contains(text(), '" + product + "')])[2]")));
                    Thread.sleep(1000);

                    Assert.assertTrue(productElement.isDisplayed(), "El producto '" + product + "' no está en el carrito.");
                    productFound = true;
                    break;
                } catch (StaleElementReferenceException e) {
                    System.out.println("El producto '" + product + "' se actualizó, volviendo a buscarlo...");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }


            if (!productFound) {
                System.out.println("Error: No se encontró el producto '" + product + "' en el carrito.");
                System.out.println("Capturando HTML actual de la tabla del carrito...");
                System.out.println(cartTable.getAttribute("outerHTML"));
                takeScreenshot("Error_Cart_" + product.replace(" ", "_"));
                Assert.fail("El producto '" + product + "' no está en el carrito.");
            }
        }

        takeScreenshot("Cart_Validation");
    }

    @Test(priority = 3)
    public void addAdditionalProduct() throws InterruptedException {
        driver.get("http://opencart.abstracta.us/index.php?route=common/home");
        //añadir dos PC HP
        System.out.println("Buscando y ingresando dos PC PH");
        addAdditionalProductToCart("HP LP3065");
        fillInput(By.id("input-option225"), getDateNextDay());
        fillInput(By.name("quantity"), "2");
        clickElement(By.id("button-cart"));
        System.out.println("Validando cantidad de memoria");
        clickElement(By.xpath("//a[text()='Specification']"));
        WebElement containMemorySize = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//strong[text()='Memory']/../../../../tbody[1]/tr/td[2]")));
        String containMemorySizeText = containMemorySize.getText().trim();
        Assert.assertTrue(containMemorySizeText.contains("16GB"), "La computadora no tiene los 16 gb de ram esperados");
        //Escribir la review del producto
        System.out.println("Añadiendo review");
        clickElement(By.xpath("//a[contains(text(),'Reviews')]"));
        fillInput(By.id("input-name"), "Marcos Medina");
        //clickElement(By.xpath("//input[@type='radio' and @value='3']"));
        Thread.sleep(1000);
        WebElement ratingGrade = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[type='radio'][value='3']")));
        ratingGrade.click();
        fillInput(By.id("input-review"), "asdqweadqweasdqwedsadfgh");
        clickElement(By.id("button-review"));
        Thread.sleep(2000);
        System.out.println("Validando alerta de warning");
        WebElement warningAlert = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[contains(@class, 'alert-danger')]")));
        String warningAlertText = warningAlert.getText().trim();
        System.out.println("Debug------ " + warningAlertText);
        Assert.assertTrue(warningAlertText.contains("Warning: Review Text must be between 25 and 1000 characters!"), "No se a mostrado la alerta de warning");
        System.out.println("Validando alerta de success");
        fillInput(By.id("input-review"), " ");
        fillInput(By.id("input-review"), "Es un equipo fabuloso 100 % recomendado");
        clickElement(By.id("button-review"));
        Thread.sleep(2000);
        WebElement successAlert = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[contains(@class, 'alert-success')]")));
        String successAlertText = successAlert.getText().trim();
        System.out.println("Debug------ " + successAlertText);
        Assert.assertTrue(successAlertText.contains("Thank you for your review. It has been submitted to the webmaster for approval."), "No se a mostrado la alerta de Success");

    }

    @Test(priority = 4)
    public void login() {
        String email = properties.getProperty("email");
        String password = properties.getProperty("password");

        driver.get("https://opencart.abstracta.us/index.php?route=account/login");
        fillInput(By.id("input-email"), email);
        fillInput(By.id("input-password"), password);
        clickElement(By.cssSelector("input[type='submit']"));
        clickElement(By.xpath("//a[contains(@href, 'route=checkout/checkout')]"));
        takeScreenshot("Login_Submitted");
    }

    @Test(priority = 5)
    public void checkout() {

        try {
            WebElement continueButtonPayment = wait.until(ExpectedConditions.elementToBeClickable(By.id("button-payment-address")));
            takeScreenshot("Payment_Address");
            continueButtonPayment.click();
            wait.until(ExpectedConditions.stalenessOf(continueButtonPayment));
        } catch (StaleElementReferenceException e) {
            System.out.println("Botón 'Continue' desapareció, volviendo a buscarlo...");
            WebElement continueButtonPayment = wait.until(ExpectedConditions.elementToBeClickable(By.id("button-payment-address")));
            continueButtonPayment.click();
        }
        takeScreenshot("Shipping_Address");
        clickElement(By.id("button-shipping-address"));

        WebElement shippingRadioButton = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//input[@name='shipping_method' and @value='flat.flat']")));
        takeScreenshot("Delivery_method");
        Assert.assertTrue(shippingRadioButton.isSelected(), "El método de envío no está seleccionado automáticamente.");

        WebElement shippingLabel = shippingRadioButton.findElement(By.xpath("./parent::label"));
        String shippingText = shippingLabel.getText().trim();
        Assert.assertTrue(shippingText.contains("Flat Shipping Rate - $5.00"), "El método de envío no es el esperado.");

        System.out.println("Validación de método de envío completada: " + shippingText);
        takeScreenshot("Shipping_Method");

        clickElement(By.id("button-shipping-method"));

        WebElement agreeCheckbox = wait.until(ExpectedConditions.elementToBeClickable(By.name("agree")));
        if (!agreeCheckbox.isSelected()) {
            agreeCheckbox.click();
        }
        takeScreenshot("Payment_method");
        clickElement(By.id("button-payment-method"));

        try {
            WebElement totalElement = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//strong[text()='Total:']/../../td[2]")
            ));
            String orderTotal = totalElement.getText().trim();
            System.out.println("Total de la orden: " + orderTotal);
            takeScreenshot("Order_Total");
        } catch (TimeoutException e) {
            System.out.println("ERROR: No se encontró el total de la orden.");
            throw e;
        }
    }

    @Test(priority = 6)
    public void validateOrderHistory() {
        try {
            clickElement(By.id("button-confirm"));
        } catch (TimeoutException e) {
            System.out.println("El botón 'Confirm Order' no apareció, es posible que la orden ya esté confirmada.");
        }

        wait.until(ExpectedConditions.urlMatches(".*route=checkout/success.*"));
        clickElement(By.xpath("//a[contains(@href, 'route=account/account')]"));
        clickElement(By.xpath("//a[contains(@href, 'route=account/order')]"));
        wait.until(ExpectedConditions.urlContains("route=account/order"));
        clickElement(By.xpath("//a[contains(@href, 'route=account/order/info')]"));

        WebElement statusElement = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("(//td[contains(text(), 'Status')])[1]/../../../tbody/tr/td[2]")));

        String orderStatus = statusElement.getText().trim();
        System.out.println("Estado de la orden: " + orderStatus);
        takeScreenshot("Order_Status");
        Assert.assertTrue(orderStatus.contains("Pending"), "La orden no está en estado 'Pending");
    }

    @Test(priority = 7)
    public void register() {
        logout();
        driver.get("https://opencart.abstracta.us/index.php?route=account/register");
        // Faker para generar correos dinámicos
        Faker faker = new Faker();
        String dynamicEmail = faker.internet().emailAddress();


        try {
            System.out.println("Ingresando datos a formulario ");
            clickElement(By.xpath("//span[contains(text(), 'My Account')]"));
            clickElement(By.linkText("Register"));
            fillInput(By.id("input-firstname"), "Marcos");
            fillInput(By.id("input-lastname"), "Medina");
            fillInput(By.id("input-email"), dynamicEmail);
            takeScreenshot("Nuevo_Email");
            fillInput(By.id("input-telephone"), "123456789");
            fillInput(By.id("input-password"), "12345678");
            fillInput(By.id("input-confirm"), "12345678");
            clickElement(By.name("agree"));
            clickElement(By.cssSelector("input[type='submit'][value='Continue']"));
        } catch (Exception e) {
            takeScreenshot("Error_General");
            throw e;
        }
        takeScreenshot("Registro exitoso");

    }

    @Test(priority = 8)
    public void compararProducto() {
        driver.get("http://opencart.abstracta.us/index.php?route=common/home");
        addProductToCompare("Apple Cinema 30");
        addProductToCompare("Samsung SyncMaster 941BW");
        clickElement(By.xpath("//a[text()=\"product comparison\"]"));
        takeScreenshot("Comparacion_exitosa");

    }

    @AfterClass
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }
}