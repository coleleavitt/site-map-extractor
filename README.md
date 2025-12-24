## Site Map Extractor

Site Map Extractor is a (fully passive) Burp Suite extension that extracts various information from the Site Map. Optionally you can select to search the full site map or just the in-scope items. There are 3 types of information that can be extracted.

### Features

#### Extract '<a href=' Links (and reports two possible vulnerabilities: 'unencrypted transport' & 'tabnabbing')
This function searches responses for links of the form ```<a href=```. Note that this will include links within javascript and in commented out areas, but not links that start differently ```<a target="ignored" href=```. The log displays the found links and the page the link was found on. You have the option to select absolute links, relative links, or both.

Log data can optionally be saved to a .csv file.

#### Extract Response Codes
This function finds all requests that returned one of the selected response code ranges (1xx/2xx/3xx/4xx/5xx). The log displays the page requested, the referer if one was specified, the specific response code, and if the response was a redirect, where the page was redirected.

The log can optionally be saved to a .csv file.

#### Export Site Map to File
This function saves the site map requests and responses to a .txt file. You can specify that all requests should be exported or only those with a corresponding response. The full content of the requests and responses is saved. This enables you to write independent code to further process the site map as you wish.

**Note:** In all cases, the extension verifies if an existing file should be over-written.

### Screenshots
##### Layout:
![Layout](/Screenshots/SME-Screenshot1.JPG)

##### File Verification:
![File Verification](/Screenshots/SME-Screenshot2.JPG)

---

## Installation

There are two versions of this extension available:

### Java Version (Recommended) - v2.0.0

The Java version uses the modern Montoya API and requires **Java 21** or later.

#### Building from Source

```bash
# Clone the repository
git clone https://github.com/coleleavitt/site-map-extractor.git
cd site-map-extractor

# Switch to the Java branch
git checkout feature/montoya-java-api

# Build the JAR
./gradlew build
```

The JAR file will be created at `build/libs/site-map-extractor-2.0.0.jar`.

#### Loading in Burp Suite

1. Open Burp Suite Professional or Community Edition
2. Go to **Extensions** → **Installed**
3. Click **Add**
4. Select **Extension type**: Java
5. Click **Select file** and choose `site-map-extractor-2.0.0.jar`
6. Click **Next** - a new tab "Site Map Extractor" will appear

### Python/Jython Version - v1.2

The original Python version requires Jython 2.7+ configured in Burp Suite.

#### Installation

1. Download [Jython Standalone JAR](https://www.jython.org/download) (2.7+)
2. In Burp Suite, go to **Extensions** → **Extensions settings**
3. Under **Python environment**, set the path to the Jython JAR
4. Go to **Extensions** → **Installed** → **Add**
5. Select **Extension type**: Python
6. Select the `site_map_extractor.py` file
7. Click **Next** - a new tab "Site Map Extractor" will appear

---

## Version History

### v2.0.0 (Java/Montoya API)
- Ported to Java using the Montoya API
- Requires Java 21+
- No Jython dependency required

### v1.2 (Python/Jython)
- UTF-8 URL encoding fix
- Fix for crash when 3XX response lacks Location header
- Original functionality preserved

---

## Development

### Requirements (Java version)
- Java 21+
- Gradle 8.x (wrapper included)

### Building
```bash
./gradlew build      # Build the JAR
./gradlew clean      # Clean build artifacts
```

### Project Structure
```
├── src/main/java/burp/
│   └── SiteMapExtractor.java    # Java extension (Montoya API)
├── site_map_extractor.py        # Python extension (legacy API)
├── build.gradle.kts             # Gradle build configuration
└── settings.gradle.kts          # Gradle settings
```

---

## Credits

- Original extension by [PortSwigger](https://github.com/PortSwigger/site-map-extractor)
- UTF-8 fix by [Zeecka](https://github.com/Zeecka/site-map-extractor)
- Java port and maintenance by [coleleavitt](https://github.com/coleleavitt/site-map-extractor)

## License

See [LICENSE](LICENSE) file.
