package org.sagebionetworks.repo.manager.oauth.claimprovider;

import org.sagebionetworks.repo.manager.UserProfileManager;
import org.sagebionetworks.repo.manager.VerificationHelper;
import org.sagebionetworks.repo.model.oauth.OIDCClaimName;
import org.sagebionetworks.repo.model.oauth.OIDCClaimsRequestDetails;
import org.sagebionetworks.repo.model.verification.VerificationSubmission;
import org.springframework.beans.factory.annotation.Autowired;

public class ValidatedLocationClaimProvider implements OIDCClaimProvider {
	@Autowired
	private UserProfileManager userProfileManager;

	@Override
	public OIDCClaimName getName() {
		return OIDCClaimName.validated_location;
	}

	@Override
	public String getDescription() {
		return "If you are a validated user, your validated location";
	}

	@Override
	public Object getClaim(String userId, OIDCClaimsRequestDetails details) {
		VerificationSubmission verificationSubmission = userProfileManager.getCurrentVerificationSubmission(Long.parseLong(userId));
		if (VerificationHelper.isVerified(verificationSubmission)) {
			return verificationSubmission.getLocation();
		} else {
			return null;
		}
	}

}
