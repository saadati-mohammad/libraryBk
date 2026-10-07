package ir.iau.library;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableTransactionManagement
@EnableJpaAuditing
public class LibraryApplication {

	public static void main(String[] args) {
		// Optional, machine-bound license gate. Disabled by default so the app starts in
		// headless environments (Docker/CI/Liara). Enable with -Dapp.license.enabled=true.
		boolean licenseEnabled = Boolean.parseBoolean(System.getProperty("app.license.enabled", "false"));
		boolean licenseInteractive = Boolean.parseBoolean(System.getProperty("app.license.interactive", "false"));
		ir.iau.library.config.KeyPromptValidator.verify(licenseEnabled, licenseInteractive);
		SpringApplication.run(LibraryApplication.class, args);
	}

}
