package org.mitre.bonnie.cqlTranslationServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.cqframework.cql.tools.formatter.CqlFormatterVisitor;
import org.cqframework.cql.tools.formatter.CqlFormatterVisitor.FormatResult;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;

/**
 * Root resource (exposed at "formatter" path).
 */
@Path("formatter")
public class FormatterResource {

  public static final String CQL_TEXT_TYPE = "text/cql";
  public static final String TARGET_FORMAT = "X-TargetFormat";

  @POST
  @Consumes(CQL_TEXT_TYPE)
  @Produces(CQL_TEXT_TYPE)
  public Response format(File cql, @Context UriInfo info) {
    FileInputStream is = null;
    try {
      is = new FileInputStream(cql);
      FormatResult result = CqlFormatterVisitor.getFormattedOutput(is);
      if( result.getErrors() != null && result.getErrors().size() > 0 ) {
        throw new FormatFailureException(result.getErrors());
      }
      return Response.ok().entity(result.getOutput()).type(CQL_TEXT_TYPE).build();
    } catch (IOException e) {
        throw new FormatFailureException(String.format("CQL format failed: %s", e.toString()));
    } finally {
      if (is != null) {
        try { is.close(); } catch( IOException iex ) { }
      }
    }
  }

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Produces(MediaType.MULTIPART_FORM_DATA)
  public Response cqlPackageToElmPackage(
          FormDataMultiPart pkg,
          @HeaderParam(TARGET_FORMAT) @DefaultValue(CQL_TEXT_TYPE) MediaType targetFormat,
          @Context UriInfo info
  ) {
    if (!targetFormat.equals(MediaType.valueOf(CQL_TEXT_TYPE))) {
      throw new FormatFailureException(
        String.format("Unsupported media type: %s. Must be text/cql.", targetFormat.toString())
      );
    }
    try {
      FormDataMultiPart translatedPkg = new FormDataMultiPart();
      for (String fieldId: pkg.getFields().keySet()) {
        for (FormDataBodyPart part: pkg.getFields(fieldId)) {
          FileInputStream is = null;
          try {
            is = new FileInputStream(part.getEntityAs(File.class));
            FormatResult result = CqlFormatterVisitor.getFormattedOutput(is);
            if( result.getErrors() != null && result.getErrors().size() > 0 ) {
                throw new FormatFailureException(result.getErrors());
            }
            translatedPkg.field(fieldId, result.getOutput(), targetFormat);
          } finally {
            if (is != null) {
              try { is.close(); } catch( IOException iex ) { }
            }
          }
        }
      }
      ResponseBuilder resp = Response.ok().type(MediaType.MULTIPART_FORM_DATA).entity(translatedPkg);
      return resp.build();
    } catch (IOException e) {
      throw new FormatFailureException(String.format("CQL format failed: %s", e.toString()));
    }
  }
}
