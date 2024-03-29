package hei.school.sarisary.endpoint.rest.controller.health;

import static hei.school.sarisary.file.FileHashAlgorithm.NONE;
import static java.io.File.createTempFile;
import static java.nio.file.Files.createTempDirectory;
import static java.util.UUID.randomUUID;

import hei.school.sarisary.PojaGenerated;
import hei.school.sarisary.file.BucketComponent;
import hei.school.sarisary.file.FileHash;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@PojaGenerated
@RestController
@AllArgsConstructor
public class HealthBucketController {

  BucketComponent bucketComponent;

  private static final String HEALTH_KEY = "health/";

  @GetMapping(value = "/health/bucket")
  public ResponseEntity<String> file_can_be_uploaded_then_signed() throws IOException {
    var fileSuffix = ".txt";
    var filePrefix = randomUUID().toString();
    var fileToUpload = createTempFile(filePrefix, fileSuffix);
    writeRandomContent(fileToUpload);
    var fileBucketKey = HEALTH_KEY + filePrefix + fileSuffix;
    can_upload_file_then_download_file(fileToUpload, fileBucketKey);

    var directoryPrefix = "dir-" + randomUUID();
    var directoryToUpload = createTempDirectory(directoryPrefix).toFile();
    var fileInDirectory =
        new File(directoryToUpload.getAbsolutePath() + "/" + randomUUID() + ".txt");
    writeRandomContent(fileInDirectory);
    var directoryBucketKey = HEALTH_KEY + directoryPrefix;
    can_upload_directory(directoryToUpload, directoryBucketKey);

    return ResponseEntity.of(Optional.of(can_presign(fileBucketKey).toString()));
  }

  @PutMapping(value = "/black-and-white/{id}")
  public ResponseEntity<Void> convertImage(
          @PathVariable String id,
          @RequestBody byte[] imageBytes) {

    try {
      MBFImage inputImage = ImageUtilities.readMBF(new ByteArrayInputStream(imageBytes));
      MBFImage grayscaleImage = convertToGrayscale(inputImage);
      MBFImage colorImage = convertToColor(grayscaleImage);

      // Save the processed images using existing logic
      saveProcessedImage(id + "_grayscale", grayscaleImage);
      saveProcessedImage(id + "_color", colorImage);

      return ResponseEntity.ok().build();

    } catch (IOException e) {
      e.printStackTrace();
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  private MBFImage convertToGrayscale(MBFImage colorImage) {
    return ColourSpaceConvertor.convert(colorImage, ColourSpace.RGB, ColourSpace.GREY);
  }

  private MBFImage convertToColor(MBFImage grayscaleImage) {
    return ColourSpaceConvertor.convert(grayscaleImage, ColourSpace.GREY, ColourSpace.RGB);
  }

  // Modify the existing saveProcessedImage method
  private void saveProcessedImage(String id, MBFImage processedImage) {
    try {
      File outputFile = new File("path/to/save/" + id + ".png");
      outputFile.getParentFile().mkdirs();
      ImageUtilities.write(processedImage, outputFile);
      System.out.println("Processed image saved: " + outputFile.getAbsolutePath());
    } catch (IOException e) {
      e.printStackTrace();
      // Handle the exception appropriately (e.g., log, return error response, etc.)
    }
  }

  private void writeRandomContent(File file) throws IOException {
    FileWriter writer = new FileWriter(file);
    var content = randomUUID().toString();
    writer.write(content);
    writer.close();
  }

  private File can_upload_file_then_download_file(File toUpload, String bucketKey)
      throws IOException {
    bucketComponent.upload(toUpload, bucketKey);

    var downloaded = bucketComponent.download(bucketKey);
    var downloadedContent = Files.readString(downloaded.toPath());
    var uploadedContent = Files.readString(toUpload.toPath());
    if (!uploadedContent.equals(downloadedContent)) {
      throw new RuntimeException("Uploaded and downloaded contents mismatch");
    }

    return downloaded;
  }

  private FileHash can_upload_directory(File toUpload, String bucketKey) {
    var hash = bucketComponent.upload(toUpload, bucketKey);
    if (!NONE.equals(hash.algorithm())) {
      throw new RuntimeException("FileHashAlgorithm.NONE expected but got: " + hash.algorithm());
    }
    return hash;
  }

  private URL can_presign(String fileBucketKey) {
    return bucketComponent.presign(fileBucketKey, Duration.ofMinutes(2));
  }
}
